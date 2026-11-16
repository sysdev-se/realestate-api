package se.sysdev.realestate.api.security;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.scraper.ScraperProperties;
import se.sysdev.realestate.api.scraper.ScraperTokenDecoder;

@Configuration
@EnableConfigurationProperties({ JwtProperties.class, ScraperProperties.class })
public class SecurityConfig {

	/**
	 * /api/v1/scraper/** accepts only scrapers (Google ID tokens, see ScraperTokenDecoder). Their
	 * authentication's name is their source. Checked before the main chain, and admin tokens
	 * don't work here, nor scraper tokens anywhere else.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain scraperFilterChain(HttpSecurity http, ScraperTokenDecoder scraperTokenDecoder,
			ScraperProperties scraperProperties) throws Exception {
		http.securityMatcher("/api/v1/scraper/**")
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((requests) -> requests.anyRequest().hasRole("SCRAPER"))
			.oauth2ResourceServer((resourceServer) -> resourceServer.jwt((jwt) -> jwt
				.decoder(scraperTokenDecoder::decode)
				.jwtAuthenticationConverter((token) -> new JwtAuthenticationToken(token,
						AuthorityUtils.createAuthorityList("ROLE_SCRAPER"),
						scraperProperties.sourceOf(token.getClaimAsString("email")).orElseThrow()))));
		return http.build();
	}

	@Bean
	ScraperTokenDecoder scraperTokenDecoder(ScraperProperties properties) {
		return new ScraperTokenDecoder(NimbusJwtDecoder.withJwkSetUri(ScraperTokenDecoder.GOOGLE_JWK_SET_URI).build(),
				properties);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf((csrf) -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/signin", "/api/v1/auth/refresh").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/consumer/**", "/hello").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				// Lets error responses through instead of turning them into 401s.
				.requestMatchers("/error").permitAll()
				.anyRequest().denyAll())
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	/**
	 * Lets the browser app (realestate-web) call the API. Only browsers enforce CORS;
	 * Postman, curl and server-side calls are unaffected. Bearer tokens, no cookies, so
	 * credentials stay off.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(allowedOrigins);
		cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setExposedHeaders(List.of("Location"));
		cors.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	/** Verifies access tokens on incoming requests. Refresh tokens are rejected. */
	@Bean
	JwtDecoder jwtDecoder(TokenService tokenService) {
		return tokenService::decodeAccessToken;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Maps the "roles" claim (e.g. ["ADMIN"]) to authorities such as ROLE_ADMIN. */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
