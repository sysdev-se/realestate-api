package se.sysdev.realestate.api.listing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "listings")
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String title;

	@Column(length = 10_000)
	private String description;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal price;

	// Batch-loaded so a page of listings doesn't issue one query per listing.
	@ElementCollection
	@CollectionTable(name = "listing_img_urls", joinColumns = @JoinColumn(name = "listing_id"))
	@OrderColumn(name = "position")
	@Column(name = "url", length = 2048, nullable = false)
	@BatchSize(size = 100)
	private List<String> imgUrls = new ArrayList<>();

	protected Listing() {
	}

	public Listing(String title, String description, BigDecimal price, List<String> imgUrls) {
		this.title = title;
		this.description = description;
		this.price = price;
		this.imgUrls = new ArrayList<>(imgUrls);
	}

	public UUID getId() {
		return this.id;
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

	public List<String> getImgUrls() {
		return this.imgUrls;
	}

}
