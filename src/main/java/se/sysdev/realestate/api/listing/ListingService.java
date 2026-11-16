package se.sysdev.realestate.api.listing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import se.sysdev.realestate.api.image.ImageService;
import se.sysdev.realestate.api.listing.ListingDtos.CreateListingRequest;
import se.sysdev.realestate.api.listing.ListingDtos.ListingResponse;

@Service
public class ListingService {
	static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "price");
	private final ListingRepository listings;
	private final ImageService imageService;
	private final TransactionTemplate transaction;

	public ListingService(ListingRepository listings, ImageService imageService, TransactionTemplate transaction) {
		this.listings = listings;
		this.imageService = imageService;
		this.transaction = transaction;
	}

	/** Published listings, for everyone. */
	@Transactional(readOnly = true)
	public Page<ListingResponse> findPublished(String title, Pageable pageable) {
		return find(PublishedStatus.PUBLISHED, title, pageable);
	}

	/** Listings in any status, for admins. */
	@Transactional(readOnly = true)
	public Page<ListingResponse> find(PublishedStatus publishedStatus, String title, Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Cannot sort by '" + order.getProperty() + "'; allowed: " + SORTABLE_PROPERTIES);
			}
		}
		Page<Listing> page = StringUtils.hasText(title)
				? this.listings.findByPublishedStatusAndTitleContainingIgnoreCase(publishedStatus, title.strip(),
						pageable)
				: this.listings.findByPublishedStatus(publishedStatus, pageable);
		return page.map(ListingResponse::from);
	}

	/** A published listing; others are not found. */
	@Transactional(readOnly = true)
	public ListingResponse getPublished(UUID id) {
		return this.listings.findByIdAndPublishedStatus(id, PublishedStatus.PUBLISHED)
			.map(ListingResponse::from)
			.orElseThrow(() -> notFound(id));
	}

	/** A listing in any status, for admins. */
	@Transactional(readOnly = true)
	public ListingResponse get(UUID id) {
		return this.listings.findById(id).map(ListingResponse::from).orElseThrow(() -> notFound(id));
	}

	@Transactional
	public ListingResponse create(CreateListingRequest request) {
		List<String> imgUrls = (request.imgUrls() != null) ? request.imgUrls() : List.of();
		return ListingResponse.from(this.listings.save(new Listing(request.details(), imgUrls)));
	}

	/**
	 * Publishes a pending listing. With {@code imgUrls} its images are replaced by those, and the
	 * ones it no longer has are deleted; without, it keeps its images.
	 */
	public ListingResponse publish(UUID id, List<String> imgUrls) {
		record Published(ListingResponse listing, List<String> removedImgUrls) {
		}
		Published published = this.transaction.execute((status) -> {
			Listing listing = this.listings.findById(id).orElseThrow(() -> notFound(id));
			List<String> removed = List.of();
			try {
				if (imgUrls != null) {
					removed = listing.publish(imgUrls);
				}
				else {
					listing.publish();
				}
			}
			catch (IllegalStateException ex) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
			}
			return new Published(ListingResponse.from(listing), removed);
		});
		this.imageService.deleteAll(published.removedImgUrls());
		return published.listing();
	}

	/** Rejects a pending listing and deletes its uploaded images. */
	public ListingResponse reject(UUID id) {
		record Rejected(ListingResponse listing, List<String> imgUrls) {
		}
		Rejected rejected = this.transaction.execute((status) -> {
			Listing listing = this.listings.findById(id).orElseThrow(() -> notFound(id));
			List<String> imgUrls;
			try {
				imgUrls = listing.reject();
			}
			catch (IllegalStateException ex) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
			}
			return new Rejected(ListingResponse.from(listing), imgUrls);
		});
		this.imageService.deleteAll(rejected.imgUrls());
		return rejected.listing();
	}

	public void delete(UUID id) {
		List<String> imgUrls = this.transaction.execute((status) -> {
			Listing listing = this.listings.findById(id).orElseThrow(() -> notFound(id));
			List<String> urls = List.copyOf(listing.getImgUrls());
			this.listings.delete(listing);
			return urls;
		});
		this.imageService.deleteAll(imgUrls);
	}

	/** Of a source's listing IDs, those not stored yet (in any status). */
	@Transactional(readOnly = true)
	public List<String> unknownSourceIds(String source, Collection<String> externalIds) {
		if (externalIds.isEmpty()) {
			return List.of();
		}
		Set<String> known = new HashSet<>(this.listings.findSourceExternalIds(source, externalIds));
		return externalIds.stream().distinct().filter((id) -> !known.contains(id)).toList();
	}

	/**
	 * Stores a scraped listing as PENDING with its images, unless the source already sent it (in
	 * any status, so rejected listings stay out). Empty when skipped. If saving fails, the images
	 * stored for it are deleted again.
	 */
	public Optional<ListingResponse> ingest(ListingSource source, ListingDetails details, List<MultipartFile> images) {
		if (this.listings.existsBySourceNameAndSourceExternalId(source.name(), source.externalId())) {
			return Optional.empty();
		}
		List<String> imgUrls = new ArrayList<>();
		try {
			for (MultipartFile image : images) {
				imgUrls.add(this.imageService.upload(image));
			}
			Listing saved = this.transaction
				.execute((status) -> this.listings.saveAndFlush(new Listing(details, source, imgUrls)));
			return Optional.of(ListingResponse.from(saved));
		}
		catch (DataIntegrityViolationException ex) {
			// The same listing arrived concurrently and was saved first.
			this.imageService.deleteAll(imgUrls);
			return Optional.empty();
		}
		catch (RuntimeException ex) {
			this.imageService.deleteAll(imgUrls);
			throw ex;
		}
	}

	private static ResponseStatusException notFound(UUID id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing " + id + " not found");
	}

}
