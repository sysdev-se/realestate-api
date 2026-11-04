package se.sysdev.realestate.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import se.sysdev.realestate.api.listing.ListingRepository;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

@ExtendWith(OutputCaptureExtension.class)
class DataSeederTests {

	private final UserRepository users = mock(UserRepository.class);

	private final ListingRepository listings = mock(ListingRepository.class);

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void seedsAdminWithHashOfConfiguredPassword() {
		seeder("s3cret-from-secret-manager").afterSingletonsInstantiated();

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		then(this.users).should().save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo(DataSeeder.ADMIN_EMAIL);
		assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
		assertThat(this.passwordEncoder.matches("s3cret-from-secret-manager", saved.getValue().getPasswordHash()))
			.isTrue();
	}

	@Test
	void skipsAdminAndWarnsWithoutPassword(CapturedOutput output) {
		seeder("").afterSingletonsInstantiated();

		then(this.users).should(never()).save(any());
		then(this.listings).should().saveAll(anyIterable());
		assertThat(output).contains("ADMIN_PASSWORD is not set");
	}

	@Test
	void doesNotOverwriteExistingAdmin() {
		given(this.users.existsByEmailIgnoreCase(DataSeeder.ADMIN_EMAIL)).willReturn(true);

		seeder("another-password").afterSingletonsInstantiated();

		then(this.users).should(never()).save(any());
	}

	private DataSeeder seeder(String adminPassword) {
		return new DataSeeder(this.users, this.listings, this.passwordEncoder, adminPassword);
	}

}
