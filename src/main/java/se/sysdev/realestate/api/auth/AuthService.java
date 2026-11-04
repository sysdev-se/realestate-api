package se.sysdev.realestate.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import se.sysdev.realestate.api.auth.AuthDtos.AccessTokenResponse;
import se.sysdev.realestate.api.auth.AuthDtos.TokenResponse;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

@Service
public class AuthService {
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	// Compared against when the email is unknown, so both failure cases take as long.
	private final String dummyPasswordHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(readOnly = true)
	public TokenResponse signIn(String email, String password) {
		Optional<User> user = this.users.findByEmailIgnoreCase(email.strip());
		String hash = user.map(User::getPasswordHash).orElse(this.dummyPasswordHash);
		if (!this.passwordEncoder.matches(password, hash) || user.isEmpty()) {
			throw unauthorized("Invalid email or password");
		}
		return new TokenResponse(this.tokenService.createAccessToken(user.get()),
				this.tokenService.createRefreshToken(user.get()));
	}

	/** Reloads the user, so a changed role or a deleted user takes effect on refresh. */
	@Transactional(readOnly = true)
	public AccessTokenResponse refresh(String refreshToken) {
		Jwt jwt;
		try {
			jwt = this.tokenService.decodeRefreshToken(refreshToken);
		}
		catch (JwtException ex) {
			throw unauthorized("Invalid refresh token");
		}
		User user = this.users.findById(UUID.fromString(jwt.getSubject()))
			.orElseThrow(() -> unauthorized("Invalid refresh token"));
		return new AccessTokenResponse(this.tokenService.createAccessToken(user));
	}

	private static ResponseStatusException unauthorized(String reason) {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
	}

}
