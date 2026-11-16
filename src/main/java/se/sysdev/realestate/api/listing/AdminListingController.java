package se.sysdev.realestate.api.listing;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import se.sysdev.realestate.api.listing.ListingDtos.CreateListingRequest;
import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;
import se.sysdev.realestate.api.listing.ListingDtos.PublishListingRequest;

// Access to /api/v1/admin/** (ADMIN only) is enforced in SecurityConfig.
@RestController
@RequestMapping("/api/v1/admin/listings")
public class AdminListingController {

	private final ListingService listingService;

	public AdminListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	/** Listings in one status, e.g. the PENDING ones waiting for review. */
	@GetMapping
	public PagedModel<ListingResponse> list(@RequestParam PublishedStatus publishedStatus,
			@RequestParam(required = false) String title,
			@PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
		return new PagedModel<>(this.listingService.find(publishedStatus, title, pageable));
	}

	/** A listing in any status. */
	@GetMapping("/{id}")
	public ListingResponse get(@PathVariable UUID id) {
		return this.listingService.get(id);
	}

	@PostMapping
	public ResponseEntity<ListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
		ListingResponse created = this.listingService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/consumer/listings/" + created.id())).body(created);
	}

	/**
	 * PENDING → PUBLISHED. An optional body sets the images it is published with (the ones it no
	 * longer has are deleted). 409 in any other status.
	 */
	@PostMapping("/{id}/publish")
	public ListingResponse publish(@PathVariable UUID id,
			@Valid @RequestBody(required = false) PublishListingRequest request) {
		return this.listingService.publish(id, (request != null) ? request.imgUrls() : null);
	}

	/** PENDING → REJECTED; deletes its images. 409 in any other status. */
	@PostMapping("/{id}/reject")
	public ListingResponse reject(@PathVariable UUID id) {
		return this.listingService.reject(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		this.listingService.delete(id);
	}

}
