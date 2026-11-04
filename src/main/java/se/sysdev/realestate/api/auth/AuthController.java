package se.sysdev.realestate.api.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.sysdev.realestate.api.auth.AuthDtos.AccessTokenResponse;
import se.sysdev.realestate.api.auth.AuthDtos.RefreshRequest;
import se.sysdev.realestate.api.auth.AuthDtos.SignInRequest;
import se.sysdev.realestate.api.auth.AuthDtos.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signin")
	public TokenResponse signIn(@Valid @RequestBody SignInRequest request) {
		return this.authService.signIn(request.email(), request.password());
	}

	@PostMapping("/refresh")
	public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return this.authService.refresh(request.refreshToken());
	}

}
