package se.sysdev.realestate.api.listing;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import se.sysdev.realestate.api.listing.ListingDtos.CreateListingRequest;
import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;

@Service
public class ListingService {

	static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "price");

	private final ListingRepository listings;

	public ListingService(ListingRepository listings) {
		this.listings = listings;
	}

	@Transactional(readOnly = true)
	public Page<ListingResponse> find(String title, Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Cannot sort by '" + order.getProperty() + "'; allowed: " + SORTABLE_PROPERTIES);
			}
		}
		Page<Listing> page = StringUtils.hasText(title)
				? this.listings.findByTitleContainingIgnoreCase(title.strip(), pageable)
				: this.listings.findAll(pageable);
		return page.map(ListingResponse::from);
	}

	@Transactional(readOnly = true)
	public ListingResponse get(UUID id) {
		return this.listings.findById(id)
			.map(ListingResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing " + id + " not found"));
	}

	@Transactional
	public ListingResponse create(CreateListingRequest request) {
		List<String> imgUrls = (request.imgUrls() != null) ? request.imgUrls() : List.of();
		Listing listing = new Listing(request.title(), request.description(), request.price(), imgUrls);
		return ListingResponse.from(this.listings.save(listing));
	}

}
