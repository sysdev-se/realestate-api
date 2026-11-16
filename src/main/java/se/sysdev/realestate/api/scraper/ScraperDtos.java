package se.sysdev.realestate.api.scraper;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import se.sysdev.realestate.api.listing.ListingDetails;
import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;
import se.sysdev.realestate.api.listing.ListingType;

public final class ScraperDtos {

	static final int MAX_IMAGES = 20;

	private ScraperDtos() {
	}

	/**
	 * A listing as a scraper found it. {@code sourceId} is the listing's ID on the website, unique
	 * per source; {@code price} is whole VND (per month for RENT). The images come as separate
	 * multipart parts.
	 */
	public record ScrapedListing(@NotBlank @Size(max = 100) String sourceId,
			@NotBlank @Size(max = 2048) @URL String sourceUrl, @NotNull ListingType type,
			@NotBlank @Size(max = 200) String title, @Size(max = 10_000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal price,
			@PositiveOrZero @Max(100) Integer bedrooms, @PositiveOrZero @Max(100) Integer bathrooms,
			@PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal areaSqm) {

		ListingDetails details() {
			return new ListingDetails(this.type, this.title, this.description, this.price, this.bedrooms,
					this.bathrooms, this.areaSqm);
		}

	}

	public enum IngestStatus {

		/** Stored as PENDING. */
		CREATED,
		/** Already known from this source (in any status); nothing stored. */
		SKIPPED

	}

	/** {@code listing} is null when skipped. */
	public record IngestResponse(IngestStatus status, ListingResponse listing) {
	}

	public record SourceIdsRequest(@NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> sourceIds) {
	}

	public record SourceIdsResponse(List<String> sourceIds) {
	}

}
