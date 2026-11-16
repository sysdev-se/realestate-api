package se.sysdev.realestate.api.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScraperPropertiesTests {

	@Test
	void sourceOfFindsTheAccountsSource() {
		ScraperProperties properties = new ScraperProperties("https://api",
				List.of("fazwaz=fazwaz@p.iam.gserviceaccount.com", " other = Other@p.iam.gserviceaccount.com "));

		assertThat(properties.sourceOf("fazwaz@p.iam.gserviceaccount.com")).contains("fazwaz");
		assertThat(properties.sourceOf("other@p.iam.gserviceaccount.com")).contains("other");
		assertThat(properties.sourceOf("someone@p.iam.gserviceaccount.com")).isEmpty();
		assertThat(properties.sourceOf(null)).isEmpty();
	}

	@Test
	void noAccountsMeansNoScrapers() {
		assertThat(new ScraperProperties(null, null).sourceOf("fazwaz@p.iam.gserviceaccount.com")).isEmpty();
	}

	@Test
	void malformedAccountsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ScraperProperties("a", List.of("fazwaz")));
		assertThatIllegalArgumentException().isThrownBy(() -> new ScraperProperties("a", List.of("=x@y")));
	}

}
