package se.sysdev.realestate.api.image;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;

/**
 * OAuth access token of the service account the app runs as, from the Cloud Run metadata server.
 * Cached until shortly before it expires (tokens live about an hour).
 */
class MetadataServerTokenProvider {
	static final String BASE_URL = "http://metadata.google.internal";
	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
	private final RestClient restClient;
	private final Clock clock;
	private String token;
	private Instant refreshAt = Instant.MIN;

	MetadataServerTokenProvider(RestClient restClient, Clock clock) {
		this.restClient = restClient;
		this.clock = clock;
	}

	synchronized String token() {
		Instant now = this.clock.instant();
		if (this.token == null || !now.isBefore(this.refreshAt)) {
			TokenResponse response = this.restClient.get()
				.uri("/computeMetadata/v1/instance/service-accounts/default/token")
				.header("Metadata-Flavor", "Google")
				.retrieve()
				.body(TokenResponse.class);
			this.token = response.accessToken();
			this.refreshAt = now.plusSeconds(response.expiresIn()).minus(REFRESH_MARGIN);
		}
		return this.token;
	}

	record TokenResponse(@JsonProperty("access_token") String accessToken,
			@JsonProperty("expires_in") long expiresIn) {
	}
}
