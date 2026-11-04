package se.sysdev.realestate.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record SignInRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	public record TokenResponse(String accessToken, String refreshToken) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record AccessTokenResponse(String accessToken) {
	}

}
