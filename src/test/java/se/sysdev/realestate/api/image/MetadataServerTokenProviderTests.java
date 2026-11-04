package se.sysdev.realestate.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetadataServerTokenProviderTests {

	private static final String TOKEN_URL = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";

	private final MutableClock clock = new MutableClock();

	@Test
	void fetchesOnceAndRefreshesShortlyBeforeExpiry() {
		RestClient.Builder builder = RestClient.builder().baseUrl(MetadataServerTokenProvider.BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(TOKEN_URL))
			.andExpect(header("Metadata-Flavor", "Google"))
			.andRespond(withSuccess("""
					{"access_token":"first","expires_in":3599,"token_type":"Bearer"}""", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(TOKEN_URL))
			.andRespond(withSuccess("""
					{"access_token":"second","expires_in":3599,"token_type":"Bearer"}""", MediaType.APPLICATION_JSON));
		MetadataServerTokenProvider tokens = new MetadataServerTokenProvider(builder.build(), this.clock);

		assertThat(tokens.token()).isEqualTo("first");
		this.clock.advance(Duration.ofMinutes(50));
		assertThat(tokens.token()).isEqualTo("first");
		this.clock.advance(Duration.ofMinutes(5));
		assertThat(tokens.token()).isEqualTo("second");
		server.verify();
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-04T00:00:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
