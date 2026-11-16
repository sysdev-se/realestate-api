package se.sysdev.realestate.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

/** Admins reviewing scraped (pending) listings. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminListingReviewTests {

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ListingRepository listings;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserRepository users;

	@Test
	void listByStatusShowsOnlyThatStatus() {
		String tag = UUID.randomUUID().toString();
		Listing pending = pending("Pending " + tag, List.of());
		this.listings.save(new Listing(details("Published " + tag), List.of()));

		assertThat(this.mvc.get()
			.uri("/api/v1/admin/listings?publishedStatus=PENDING&title={title}", tag)
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"content":[{"id":"%s","publishedStatus":"PENDING","source":"test",
					 "sourceUrl":"https://example.com/%s"}],"page":{"totalElements":1}}""".formatted(pending.getId(),
					pending.getSource().externalId()));
	}

	@Test
	void listRequiresAStatus() {
		assertThat(this.mvc.get().uri("/api/v1/admin/listings").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void reviewEndpointsRequireAnAdmin() {
		Listing pending = pending("Pending", List.of());
		assertThat(this.mvc.get().uri("/api/v1/admin/listings?publishedStatus=PENDING"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get()
			.uri("/api/v1/admin/listings/{id}", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.CONSUMER))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.CONSUMER))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.post().uri("/api/v1/admin/listings/{id}/reject", pending.getId()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminCanGetAPendingListing() {
		Listing pending = pending("Pending", List.of());

		assertThat(this.mvc.get()
			.uri("/api/v1/admin/listings/{id}", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"publishedStatus":"PENDING"}""");
	}

	@Test
	void publishMakesThePendingListingPublic() {
		Listing pending = pending("To publish", List.of());
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", pending.getId()))
			.hasStatus(HttpStatus.NOT_FOUND);

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"publishedStatus":"PUBLISHED"}""");

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", pending.getId())).hasStatus(HttpStatus.OK);
	}

	@Test
	void publishWithImagesReplacesTheListingsImages() throws Exception {
		String kept = uploadImage();
		String dropped = uploadImage();
		String added = uploadImage();
		Listing pending = pending("To publish with images", List.of(dropped, kept));

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"imgUrls":["%s","%s"]}""".formatted(added, kept)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"publishedStatus":"PUBLISHED","primaryImgUrl":"%s","imgUrls":["%s","%s"]}"""
				.formatted(added, added, kept));

		assertThat(this.mvc.get().uri(added)).hasStatus(HttpStatus.OK);
		assertThat(this.mvc.get().uri(kept)).hasStatus(HttpStatus.OK);
		assertThat(this.mvc.get().uri(dropped)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void publishWithInvalidImagesIsRejectedAndChangesNothing() throws Exception {
		String image = uploadImage();
		Listing pending = pending("Invalid images", List.of(image));

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"imgUrls":["not a url"]}""")).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(this.listings.findById(pending.getId())).get()
			.satisfies((listing) -> assertThat(listing.getPublishedStatus()).isEqualTo(PublishedStatus.PENDING));
		assertThat(this.mvc.get().uri(image)).hasStatus(HttpStatus.OK);
	}

	@Test
	void publishWithImagesKeepsTheImagesOfANonPendingListing() throws Exception {
		String image = uploadImage();
		Listing published = this.listings.save(new Listing(details("Already published"), List.of(image)));

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", published.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"imgUrls":[]}""")).hasStatus(HttpStatus.CONFLICT);

		assertThat(this.mvc.get().uri(image)).hasStatus(HttpStatus.OK);
	}

	@Test
	void rejectKeepsTheListingHiddenAndDeletesItsImages() throws Exception {
		String imageUrl = uploadImage();
		Listing pending = pending("To reject", List.of(imageUrl));
		assertThat(this.mvc.get().uri(imageUrl)).hasStatus(HttpStatus.OK);

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/reject", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"publishedStatus":"REJECTED","imgUrls":[],"primaryImgUrl":null}""");

		assertThat(this.mvc.get().uri(imageUrl)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", pending.getId()))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.listings.existsBySourceNameAndSourceExternalId("test", pending.getSource().externalId()))
			.isTrue();
	}

	@Test
	void onlyPendingListingsCanBePublishedOrRejected() {
		Listing published = this.listings.save(new Listing(details("Published"), List.of()));

		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", published.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/reject", published.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/publish", UUID.randomUUID())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.NOT_FOUND);
	}

	private Listing pending(String title, List<String> imgUrls) {
		String sourceId = UUID.randomUUID().toString();
		return this.listings.save(new Listing(details(title),
				new ListingSource("test", sourceId, "https://example.com/" + sourceId), imgUrls));
	}

	private String uploadImage() throws Exception {
		MvcTestResult uploaded = this.mvc.post()
			.uri("/api/v1/admin/images")
			.multipart()
			.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.exchange();
		return JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");
	}

	private static ListingDetails details(String title) {
		return new ListingDetails(ListingType.RENT, title, null, new BigDecimal("30000000"), null, null, null);
	}

	private String bearer(Role role) {
		return "Bearer " + this.tokenService.createAccessToken(user(role));
	}

	private User user(Role role) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
		return this.users.save(new User("Test " + role, email, null, role, "unused"));
	}

}
