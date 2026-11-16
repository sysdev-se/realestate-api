package se.sysdev.realestate.api.scraper;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which callers may use /api/v1/scraper/**: Google ID tokens issued for {@code audience} (this
 * API's URL) to one of the service accounts in {@code accounts}. Each account entry is
 * {@code source=email}, e.g. {@code fazwaz=realestate-scraper-fazwaz@<project>.iam.gserviceaccount.com};
 * the source is what the API records for that scraper's listings. Unset: no scraper gets in.
 */
@ConfigurationProperties("app.scraper")
public record ScraperProperties(String audience, List<String> accounts) {

	public ScraperProperties {
		accounts = (accounts != null) ? List.copyOf(accounts) : List.of();
		for (String account : accounts) {
			String[] parts = account.split("=", -1);
			if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
				throw new IllegalArgumentException(
						"app.scraper.accounts entries must be source=email, got '" + account + "'");
			}
		}
	}

	/** The source of the scraper that runs as this service account, if it is one. */
	public Optional<String> sourceOf(String email) {
		if (email == null) {
			return Optional.empty();
		}
		return this.accounts.stream()
			.map((account) -> account.split("=", 2))
			.filter((parts) -> parts[1].strip().equalsIgnoreCase(email))
			.map((parts) -> parts[0].strip())
			.findFirst();
	}

}
