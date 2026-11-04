package se.sysdev.realestate.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import se.sysdev.realestate.api.security.JwtProperties;
import se.sysdev.realestate.api.user.User;

/**
 * Issues and verifies HS256 JWTs. Access and refresh tokens use different secrets and
 * carry a {@code token_type} claim, so one can never be used as the other.
 */
@Service
public class TokenService {

	public static final String ROLES_CLAIM = "roles";

	static final String TOKEN_TYPE_CLAIM = "token_type";

	private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

	private final JwtProperties properties;

	private final JwtEncoder accessEncoder;

	private final JwtDecoder accessDecoder;

	private final JwtEncoder refreshEncoder;

	private final JwtDecoder refreshDecoder;

	public TokenService(JwtProperties properties) {
		this.properties = properties;
		SecretKey accessKey = key(properties.access().secret());
		SecretKey refreshKey = key(properties.refresh().secret());
		this.accessEncoder = NimbusJwtEncoder.withSecretKey(accessKey).algorithm(ALGORITHM).build();
		this.accessDecoder = decoder(accessKey, properties.issuer(), TokenType.ACCESS);
		this.refreshEncoder = NimbusJwtEncoder.withSecretKey(refreshKey).algorithm(ALGORITHM).build();
		this.refreshDecoder = decoder(refreshKey, properties.issuer(), TokenType.REFRESH);
	}

	public String createAccessToken(User user) {
		JwtClaimsSet claims = claims(user, TokenType.ACCESS)
			.claim(ROLES_CLAIM, List.of(user.getRole().name()))
			.build();
		return encode(this.accessEncoder, claims);
	}

	public String createRefreshToken(User user) {
		return encode(this.refreshEncoder, claims(user, TokenType.REFRESH).build());
	}

	public Jwt decodeAccessToken(String token) throws JwtException {
		return this.accessDecoder.decode(token);
	}

	public Jwt decodeRefreshToken(String token) throws JwtException {
		return this.refreshDecoder.decode(token);
	}

	private JwtClaimsSet.Builder claims(User user, TokenType type) {
		Instant now = Instant.now();
		JwtProperties.Token settings = (type == TokenType.ACCESS) ? this.properties.access()
				: this.properties.refresh();
		return JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(settings.ttl()))
			.claim(TOKEN_TYPE_CLAIM, type.claimValue);
	}

	private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(ALGORITHM).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static NimbusJwtDecoder decoder(SecretKey key, String issuer, TokenType type) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
				new JwtClaimValidator<String>(TOKEN_TYPE_CLAIM, type.claimValue::equals)));
		return decoder;
	}

	private static SecretKey key(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private enum TokenType {

		ACCESS("access"), REFRESH("refresh");

		private final String claimValue;

		TokenType(String claimValue) {
			this.claimValue = claimValue;
		}

	}

}
