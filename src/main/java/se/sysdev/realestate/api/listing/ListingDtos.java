package se.sysdev.realestate.api.listing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public final class ListingDtos {

	private ListingDtos() {
	}

	/**
	 * A listing. {@code price} is whole VND (per month for RENT). {@code source} and
	 * {@code sourceUrl} are null unless a scraper found it.
	 */
	public record ListingResponse(UUID id, PublishedStatus publishedStatus, ListingType type, String title,
			String description, BigDecimal price, Integer bedrooms, Integer bathrooms, BigDecimal areaSqm,
			String source, String sourceUrl, String primaryImgUrl, List<String> imgUrls) {

		static ListingResponse from(Listing listing) {
			List<String> imgUrls = List.copyOf(listing.getImgUrls());
			ListingSource source = listing.getSource();
			return new ListingResponse(listing.getId(), listing.getPublishedStatus(), listing.getType(),
					listing.getTitle(), listing.getDescription(), listing.getPrice(), listing.getBedrooms(),
					listing.getBathrooms(), listing.getAreaSqm(), (source != null) ? source.name() : null,
					(source != null) ? source.url() : null, imgUrls.isEmpty() ? null : imgUrls.getFirst(), imgUrls);
		}

	}

	public record CreateListingRequest(@NotNull ListingType type, @NotBlank @Size(max = 200) String title,
			@Size(max = 10_000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal price,
			@PositiveOrZero @Max(100) Integer bedrooms, @PositiveOrZero @Max(100) Integer bathrooms,
			@PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal areaSqm,
			@Size(max = 50) List<@NotBlank @Size(max = 2048) @URL String> imgUrls) {

		ListingDetails details() {
			return new ListingDetails(this.type, this.title, this.description, this.price, this.bedrooms,
					this.bathrooms, this.areaSqm);
		}

	}

	/** The images a pending listing is published with, replacing the ones it has. */
	public record PublishListingRequest(
			@NotNull @Size(max = 50) List<@NotBlank @Size(max = 2048) @URL String> imgUrls) {
	}

}
