package se.sysdev.realestate.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AdminListingControllerTests {

	private static final String NEW_LISTING = """
			{"title":"Admin listing","description":"Created in a test","price":1234567.89,
			 "imgUrls":["https://example.com/1.jpg"]}""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserRepository users;

	@Test
	void createWithoutTokenIsUnauthorized() {
		assertThat(create(NEW_LISTING)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createWithInvalidTokenIsUnauthorized() {
		assertThat(create(NEW_LISTING).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createWithRefreshTokenIsUnauthorized() {
		String refreshToken = this.tokenService.createRefreshToken(user(Role.ADMIN));
		assertThat(create(NEW_LISTING).header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createWithConsumerTokenIsForbidden() {
		assertThat(create(NEW_LISTING).header(HttpHeaders.AUTHORIZATION, bearer(Role.CONSUMER)))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void createWithAdminTokenCreatesListing() throws Exception {
		MvcTestResult result = create(NEW_LISTING).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)).exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"title":"Admin listing","price":1234567.89,"imgUrls":["https://example.com/1.jpg"]}""");

		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		assertThat(result).headers().hasValue(HttpHeaders.LOCATION, "/api/v1/consumer/listings/" + id);
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", id)).hasStatus(HttpStatus.OK);
	}

	@Test
	void createWithInvalidBodyIsBadRequest() {
		assertThat(create("""
				{"title":"","price":-1,"imgUrls":["not a url"]}""").header(HttpHeaders.AUTHORIZATION,
				bearer(Role.ADMIN)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void deleteRemovesTheListingAndItsUploadedImages() throws Exception {
		byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };
		MvcTestResult uploaded = this.mvc.post()
			.uri("/api/v1/admin/images")
			.multipart()
			.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.exchange();
		String imageUrl = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");
		MvcTestResult created = create("""
				{"title":"To delete","price":1,"imgUrls":["%s","https://example.com/kept.jpg"]}""".formatted(imageUrl))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.exchange();
		assertThat(created).bodyJson()
			.hasPathSatisfying("$.primaryImgUrl", (url) -> url.assertThat().isEqualTo(imageUrl));
		String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		assertThat(this.mvc.get().uri(imageUrl)).hasStatus(HttpStatus.OK);

		assertThat(this.mvc.delete().uri("/api/v1/admin/listings/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri(imageUrl)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteOfAnUnknownListingIsNotFound() {
		assertThat(this.mvc.delete().uri("/api/v1/admin/listings/{id}", UUID.randomUUID())
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteRequiresAnAdmin() {
		String id = UUID.randomUUID().toString();
		assertThat(this.mvc.delete().uri("/api/v1/admin/listings/{id}", id)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.delete().uri("/api/v1/admin/listings/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.CONSUMER))).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MockMvcRequestBuilder create(String body) {
		return this.mvc.post().uri("/api/v1/admin/listings").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private String bearer(Role role) {
		return "Bearer " + this.tokenService.createAccessToken(user(role));
	}

	private User user(Role role) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
		return this.users.save(new User("Test " + role, email, null, role, "unused"));
	}

}
