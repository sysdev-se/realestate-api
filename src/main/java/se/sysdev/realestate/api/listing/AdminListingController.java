package se.sysdev.realestate.api.listing;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.sysdev.realestate.api.listing.ListingDtos.CreateListingRequest;
import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;

// Access to /api/v1/admin/** (ADMIN only) is enforced in SecurityConfig.
@RestController
@RequestMapping("/api/v1/admin/listings")
public class AdminListingController {

	private final ListingService listingService;

	public AdminListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	@PostMapping
	public ResponseEntity<ListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
		ListingResponse created = this.listingService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/consumer/listings/" + created.id())).body(created);
	}

}
