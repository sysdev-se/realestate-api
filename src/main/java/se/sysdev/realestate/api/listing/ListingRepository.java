package se.sysdev.realestate.api.listing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

	Page<Listing> findByPublishedStatus(PublishedStatus publishedStatus, Pageable pageable);

	Page<Listing> findByPublishedStatusAndTitleContainingIgnoreCase(PublishedStatus publishedStatus, String title,
			Pageable pageable);

	Optional<Listing> findByIdAndPublishedStatus(UUID id, PublishedStatus publishedStatus);

	boolean existsBySourceNameAndSourceExternalId(String name, String externalId);

	@Query("select l.source.externalId from Listing l where l.source.name = :name and l.source.externalId in :externalIds")
	List<String> findSourceExternalIds(String name, Collection<String> externalIds);

}
