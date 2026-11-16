package se.sysdev.realestate.api.scraper;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Verifies the ID tokens scrapers send: signed by Google, for this API (audience), and for a
 * verified service account listed in {@link ScraperProperties}. Scrapers on Cloud Run get them
 * from the metadata server (with {@code format=full}, so they carry the email).
 * <p>
 * Deliberately not a JwtDecoder bean: that one verifies the API's own access tokens.
 */
public class ScraperTokenDecoder {

	/** Google's signing keys for ID tokens. */
	public static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

	static final List<String> GOOGLE_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

	private final NimbusJwtDecoder decoder;

	/** {@code decoder} checks the signature; this adds the checks on the claims. */
	public ScraperTokenDecoder(NimbusJwtDecoder decoder, ScraperProperties properties) {
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
				new JwtClaimValidator<Object>(JwtClaimNames.ISS, (iss) -> GOOGLE_ISSUERS.contains(String.valueOf(iss))),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						(aud) -> StringUtils.hasText(properties.audience()) && aud.contains(properties.audience())),
				new JwtClaimValidator<Object>("email_verified", Boolean.TRUE::equals),
				new JwtClaimValidator<String>("email", (email) -> properties.sourceOf(email).isPresent())));
		this.decoder = decoder;
	}

	public Jwt decode(String token) throws JwtException {
		return this.decoder.decode(token);
	}

}
