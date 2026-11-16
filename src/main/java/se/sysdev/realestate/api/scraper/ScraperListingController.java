package se.sysdev.realestate.api.scraper;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import se.sysdev.realestate.api.listing.ListingService;
import se.sysdev.realestate.api.listing.ListingSource;
import se.sysdev.realestate.api.scraper.ScraperDtos.IngestResponse;
import se.sysdev.realestate.api.scraper.ScraperDtos.IngestStatus;
import se.sysdev.realestate.api.scraper.ScraperDtos.ScrapedListing;
import se.sysdev.realestate.api.scraper.ScraperDtos.SourceIdsRequest;
import se.sysdev.realestate.api.scraper.ScraperDtos.SourceIdsResponse;

/**
 * Where scrapers deliver listings. Only scrapers get in (SecurityConfig); the authentication's
 * name is the scraper's source, so a scraper can only write listings of its own source.
 */
@RestController
@RequestMapping("/api/v1/scraper/listings")
public class ScraperListingController {

	private final ListingService listingService;

	public ScraperListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	/**
	 * Stores one listing as PENDING with its images (multipart: a JSON {@code listing} part and up
	 * to 20 {@code images} parts, JPEG, PNG or WebP; the first is the primary image). 201 when
	 * created, 200 with status SKIPPED when this source already sent it.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<IngestResponse> ingest(Authentication scraper,
			@Valid @RequestPart("listing") ScrapedListing listing,
			@RequestPart(name = "images", required = false) List<MultipartFile> images) {
		List<MultipartFile> files = (images != null) ? images : List.of();
		if (files.size() > ScraperDtos.MAX_IMAGES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"At most " + ScraperDtos.MAX_IMAGES + " images per listing");
		}
		ListingSource source = new ListingSource(scraper.getName(), listing.sourceId(), listing.sourceUrl());
		return this.listingService.ingest(source, listing.details(), files)
			.map((created) -> ResponseEntity.created(URI.create("/api/v1/admin/listings/" + created.id()))
				.body(new IngestResponse(IngestStatus.CREATED, created)))
			.orElseGet(() -> ResponseEntity.ok(new IngestResponse(IngestStatus.SKIPPED, null)));
	}

	/**
	 * Of the given source IDs, those the API doesn't have yet, so the scraper only fetches details
	 * and images of new listings.
	 */
	@PostMapping("/unknown-ids")
	public SourceIdsResponse unknownIds(Authentication scraper, @Valid @RequestBody SourceIdsRequest request) {
		return new SourceIdsResponse(this.listingService.unknownSourceIds(scraper.getName(), request.sourceIds()));
	}

}
