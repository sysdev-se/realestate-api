package se.sysdev.realestate.api.listing;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

	Page<Listing> findByTitleContainingIgnoreCase(String title, Pageable pageable);

}
