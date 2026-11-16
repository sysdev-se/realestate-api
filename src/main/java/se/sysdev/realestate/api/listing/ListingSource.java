package se.sysdev.realestate.api.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Where a scraped listing came from: the scraper's source name (e.g. "fazwaz"), the listing's
 * ID on that website and its URL there. A source and ID pair is stored only once.
 */
@Embeddable
public record ListingSource(@Column(name = "source", length = 50) String name,
		@Column(name = "source_id", length = 100) String externalId,
		@Column(name = "source_url", length = 2048) String url) {
}
