package se.sysdev.realestate.api.listing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public final class ListingDtos {

	private ListingDtos() {
	}

	public record ListingResponse(UUID id, String title, String description, BigDecimal price, List<String> imgUrls) {

		static ListingResponse from(Listing listing) {
			return new ListingResponse(listing.getId(), listing.getTitle(), listing.getDescription(),
					listing.getPrice(), List.copyOf(listing.getImgUrls()));
		}

	}

	public record CreateListingRequest(@NotBlank @Size(max = 200) String title,
			@Size(max = 10_000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal price,
			@Size(max = 50) List<@NotBlank @Size(max = 2048) @URL String> imgUrls) {
	}

}
