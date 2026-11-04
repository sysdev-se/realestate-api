package se.sysdev.realestate.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. Secrets come from the ACCESS_TOKEN_SECRET and REFRESH_TOKEN_SECRET
 * environment variables (see application.properties).
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String issuer, Token access, Token refresh) {

	// HS256 requires a key of at least 256 bits.
	static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		requireValid("access", access);
		requireValid("refresh", refresh);
		if (access.secret().equals(refresh.secret())) {
			throw new IllegalArgumentException("app.jwt.access.secret and app.jwt.refresh.secret must differ");
		}
	}

	private static void requireValid(String name, Token token) {
		if (token == null || token.secret() == null || token.ttl() == null) {
			throw new IllegalArgumentException("app.jwt." + name + ".secret and app.jwt." + name + ".ttl are required");
		}
		if (token.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"app.jwt." + name + ".secret must be at least " + MIN_SECRET_BYTES + " bytes");
		}
	}

	public record Token(String secret, Duration ttl) {
	}

}
