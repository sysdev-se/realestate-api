package se.sysdev.realestate.api.listing;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;

@RestController
@RequestMapping("/api/v1/consumer/listings")
public class ConsumerListingController {

	private final ListingService listingService;

	public ConsumerListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	@GetMapping
	public PagedModel<ListingResponse> list(@RequestParam(required = false) String title,
			@PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
		return new PagedModel<>(this.listingService.find(title, pageable));
	}

	@GetMapping("/{id}")
	public ListingResponse get(@PathVariable UUID id) {
		return this.listingService.get(id);
	}

}
