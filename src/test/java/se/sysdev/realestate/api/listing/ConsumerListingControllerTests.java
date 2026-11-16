package se.sysdev.realestate.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class ConsumerListingControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ListingRepository listings;

	@Test
	void listReturnsSeededListingsInPagedFormat() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings?size=2"))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.hasPathSatisfying("$.content.length()", (length) -> length.assertThat().isEqualTo(2))
			.hasPathSatisfying("$.page.size", (size) -> size.assertThat().isEqualTo(2))
			.hasPathSatisfying("$.page.number", (number) -> number.assertThat().isEqualTo(0))
			.hasPathSatisfying("$.page.totalElements", (total) -> total.assertThat().asNumber().isNotNull());
	}

	@Test
	void listFiltersByTitleCaseInsensitivelyAndSorts() {
		String tag = UUID.randomUUID().toString();
		this.listings.save(new Listing(details("Cheap " + tag, "100"), List.of()));
		this.listings.save(new Listing(details("Pricey " + tag.toUpperCase(), "900"), List.of()));
		// Not published, so not listed.
		this.listings.save(new Listing(details("Pending " + tag, "500"),
				new ListingSource("test", UUID.randomUUID().toString(), "https://example.com/1"), List.of()));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings?title={title}&sort=price,desc", tag))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"content":[{"price":900},{"price":100}],"page":{"totalElements":2}}""");
	}

	@Test
	void listWithUnknownSortPropertyIsBadRequest() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings?sort=imgUrls")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void pageSizeIsCapped() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings?size=1000"))
			.bodyJson()
			.hasPathSatisfying("$.page.size", (size) -> size.assertThat().isEqualTo(100));
	}

	@Test
	void getReturnsListing() {
		Listing saved = this.listings.save(new Listing(
				new ListingDetails(ListingType.RENT, "Get me", "Desc", new BigDecimal("42000000"), 3, 2,
						new BigDecimal("120.50")),
				List.of("https://example.com/a.jpg", "https://example.com/b.jpg")));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", saved.getId()))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id":"%s","publishedStatus":"PUBLISHED","type":"RENT","title":"Get me","description":"Desc",
					 "price":42000000,"bedrooms":3,"bathrooms":2,"areaSqm":120.50,"source":null,"sourceUrl":null,
					 "primaryImgUrl":"https://example.com/a.jpg",
					 "imgUrls":["https://example.com/a.jpg","https://example.com/b.jpg"]}""".formatted(saved.getId()));
	}

	@Test
	void primaryImgUrlIsNullWithoutImages() {
		Listing saved = this.listings.save(new Listing(details("No images", "1"), List.of()));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", saved.getId()))
			.bodyJson()
			.hasPathSatisfying("$.primaryImgUrl", (url) -> url.assertThat().isNull());
	}

	@Test
	void getOfAnUnpublishedListingIsNotFound() {
		Listing pending = this.listings.save(new Listing(details("Pending", "1"),
				new ListingSource("test", UUID.randomUUID().toString(), "https://example.com/1"), List.of()));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", pending.getId()))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void getUnknownListingIsNotFound() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", UUID.randomUUID()))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void getWithMalformedIdIsBadRequest() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private static ListingDetails details(String title, String price) {
		return new ListingDetails(ListingType.SALE, title, null, new BigDecimal(price), null, null, null);
	}

}
