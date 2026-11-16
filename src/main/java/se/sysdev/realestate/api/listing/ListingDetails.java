package se.sysdev.realestate.api.listing;

import java.math.BigDecimal;

/**
 * What describes a listing, whoever created it. The price is in VND: the sale price or, for
 * RENT, the rent per month. Bedrooms, bathrooms and area are optional.
 */
public record ListingDetails(ListingType type, String title, String description, BigDecimal price,
		Integer bedrooms, Integer bathrooms, BigDecimal areaSqm) {
}
