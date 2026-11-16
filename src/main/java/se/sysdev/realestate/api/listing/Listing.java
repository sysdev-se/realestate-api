package se.sysdev.realestate.api.listing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "listings",
		uniqueConstraints = @UniqueConstraint(name = "uk_listings_source", columnNames = { "source", "source_id" }),
		indexes = @Index(name = "ix_listings_published_status", columnList = "published_status"))
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PublishedStatus publishedStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private ListingType type;

	@Column(nullable = false)
	private String title;

	@Column(length = 10_000)
	private String description;

	// Whole VND.
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal price;

	private Integer bedrooms;

	private Integer bathrooms;

	@Column(precision = 10, scale = 2)
	private BigDecimal areaSqm;

	// Null for listings an admin created.
	@Embedded
	private ListingSource source;

	// Batch-loaded so a page of listings doesn't issue one query per listing.
	@ElementCollection
	@CollectionTable(name = "listing_img_urls", joinColumns = @JoinColumn(name = "listing_id"))
	@OrderColumn(name = "position")
	@Column(name = "url", length = 2048, nullable = false)
	@BatchSize(size = 100)
	private List<String> imgUrls = new ArrayList<>();

	protected Listing() {
	}

	/** A listing an admin created: public right away. */
	public Listing(ListingDetails details, List<String> imgUrls) {
		this(details, null, PublishedStatus.PUBLISHED, imgUrls);
	}

	/** A listing a scraper found: pending until an admin publishes it. */
	public Listing(ListingDetails details, ListingSource source, List<String> imgUrls) {
		this(details, source, PublishedStatus.PENDING, imgUrls);
	}

	private Listing(ListingDetails details, ListingSource source, PublishedStatus publishedStatus,
			List<String> imgUrls) {
		this.publishedStatus = publishedStatus;
		this.type = details.type();
		this.title = details.title();
		this.description = details.description();
		this.price = details.price();
		this.bedrooms = details.bedrooms();
		this.bathrooms = details.bathrooms();
		this.areaSqm = details.areaSqm();
		this.source = source;
		this.imgUrls = new ArrayList<>(imgUrls);
	}

	/** Makes a pending listing public. */
	void publish() {
		requirePending();
		this.publishedStatus = PublishedStatus.PUBLISHED;
	}

	/**
	 * Makes a pending listing public with the images the admin chose (the first is the cover).
	 * Returns the URLs of the images it had before and no longer has, for deleting.
	 */
	List<String> publish(List<String> imgUrls) {
		publish();
		List<String> removed = this.imgUrls.stream().filter((url) -> !imgUrls.contains(url)).toList();
		this.imgUrls.clear();
		this.imgUrls.addAll(imgUrls);
		return removed;
	}

	/**
	 * Rejects a pending listing. The row stays, so its source can't add it again, but its images
	 * are dropped: returns their URLs for deleting.
	 */
	List<String> reject() {
		requirePending();
		this.publishedStatus = PublishedStatus.REJECTED;
		List<String> removed = List.copyOf(this.imgUrls);
		this.imgUrls.clear();
		return removed;
	}

	private void requirePending() {
		if (this.publishedStatus != PublishedStatus.PENDING) {
			throw new IllegalStateException("Listing " + this.id + " is " + this.publishedStatus + ", not PENDING");
		}
	}

	public UUID getId() {
		return this.id;
	}

	public PublishedStatus getPublishedStatus() {
		return this.publishedStatus;
	}

	public ListingType getType() {
		return this.type;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescription() {
		return this.description;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	public Integer getBedrooms() {
		return this.bedrooms;
	}

	public Integer getBathrooms() {
		return this.bathrooms;
	}

	public BigDecimal getAreaSqm() {
		return this.areaSqm;
	}

	public ListingSource getSource() {
		return this.source;
	}

	public List<String> getImgUrls() {
		return this.imgUrls;
	}

}
