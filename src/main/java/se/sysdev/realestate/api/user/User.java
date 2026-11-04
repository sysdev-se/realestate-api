package se.sysdev.realestate.api.user;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// "user" is a reserved word in H2 and PostgreSQL.
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String username;

	@Column(nullable = false, unique = true)
	private String email;

	private String telephoneNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private String passwordHash;

	protected User() {
	}

	public User(String username, String email, String telephoneNumber, Role role, String passwordHash) {
		this.username = username;
		this.email = email;
		this.telephoneNumber = telephoneNumber;
		this.role = role;
		this.passwordHash = passwordHash;
	}

	public UUID getId() {
		return this.id;
	}

	public String getUsername() {
		return this.username;
	}

	public String getEmail() {
		return this.email;
	}

	public String getTelephoneNumber() {
		return this.telephoneNumber;
	}

	public Role getRole() {
		return this.role;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

}
