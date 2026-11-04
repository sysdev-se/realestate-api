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
		this.listings.save(new Listing("Cheap " + tag, null, new BigDecimal("100.00"), List.of()));
		this.listings.save(new Listing("Pricey " + tag.toUpperCase(), null, new BigDecimal("900.00"), List.of()));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings?title={title}&sort=price,desc", tag))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"content":[{"price":900.00},{"price":100.00}],"page":{"totalElements":2}}""");
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
		Listing saved = this.listings.save(new Listing("Get me", "Desc", new BigDecimal("42.50"),
				List.of("https://example.com/a.jpg", "https://example.com/b.jpg")));

		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", saved.getId()))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id":"%s","title":"Get me","description":"Desc","price":42.50,
					 "imgUrls":["https://example.com/a.jpg","https://example.com/b.jpg"]}""".formatted(saved.getId()));
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

}
