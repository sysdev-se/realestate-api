package se.sysdev.realestate.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import se.sysdev.realestate.api.auth.TokenService;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf((csrf) -> csrf.disable())
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
