package se.sysdev.realestate.api.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import se.sysdev.realestate.api.listing.Listing;
import se.sysdev.realestate.api.listing.ListingRepository;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

/**
 * Fills the in-memory database at startup. Skips anything that already exists, so it is
 * safe to keep running once a persistent database is attached.
 * <p>
 * The admin's password comes from ADMIN_PASSWORD (Secret Manager on Cloud Run). Without
 * it no admin is created, so the app never runs with a known admin password.
 * <p>
 * Runs after all beans are created but before the web server opens its port, so no
 * request can arrive before the data exists (an ApplicationRunner runs after the port opens).
 */
@Component
public class DataSeeder implements SmartInitializingSingleton {
	private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);
	static final String ADMIN_EMAIL = "rikard.virta@sysdev.se";
	private final UserRepository users;
	private final ListingRepository listings;
	private final PasswordEncoder passwordEncoder;
	private final String adminPassword;

	public DataSeeder(
			UserRepository users,
			ListingRepository listings,
			PasswordEncoder passwordEncoder,
			@Value("${app.seed.admin-password:}") String adminPassword) {
		this.users = users;
		this.listings = listings;
		this.passwordEncoder = passwordEncoder;
		this.adminPassword = adminPassword;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (!StringUtils.hasText(this.adminPassword)) {
			logger.warn("ADMIN_PASSWORD is not set; admin user {} not seeded", ADMIN_EMAIL);
		}
		else if (!this.users.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
			String hash = this.passwordEncoder.encode(this.adminPassword);
			this.users.save(new User("Rikard Virta", ADMIN_EMAIL, "0328774484", Role.ADMIN, hash));
			logger.info("Seeded admin user {}", ADMIN_EMAIL);
		}
		if (this.listings.count() == 0) {
			this.listings.saveAll(sampleListings());
			logger.info("Seeded sample listings");
		}
	}

	private static List<Listing> sampleListings() {
		return List.of(
				new Listing("Seaside villa", "Four bedrooms, private beach access and a large terrace.",
						new BigDecimal("12500000.00"),
						List.of("https://picsum.photos/seed/seaside-villa-1/800/600",
								"https://picsum.photos/seed/seaside-villa-2/800/600")),
				new Listing("City centre apartment", "Two rooms on the 12th floor, walking distance to the metro.",
						new BigDecimal("3450000.00"), List.of("https://picsum.photos/seed/city-apartment-1/800/600")),
				new Listing("Countryside cottage", "Renovated cottage with a garden and a sauna.",
						new BigDecimal("1890000.00"),
						List.of("https://picsum.photos/seed/cottage-1/800/600",
								"https://picsum.photos/seed/cottage-2/800/600",
								"https://picsum.photos/seed/cottage-3/800/600")));
	}

}
