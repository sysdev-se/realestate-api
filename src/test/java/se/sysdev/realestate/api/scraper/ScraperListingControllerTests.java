package se.sysdev.realestate.api.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMultipartMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.listing.ListingRepository;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

/**
 * Scraper ingest, with a local RSA key standing in for Google's: the same claim checks as in
 * production, only the signing key differs.
 */
@SpringBootTest(properties = { "app.scraper.audience=" + ScraperListingControllerTests.AUDIENCE,
		"app.scraper.accounts=fazwaz=" + ScraperListingControllerTests.FAZWAZ + ",other="
				+ ScraperListingControllerTests.OTHER })
@AutoConfigureMockMvc
class ScraperListingControllerTests {

	static final String AUDIENCE = "https://realestate-api.test";

	static final String FAZWAZ = "realestate-scraper-fazwaz@test.iam.gserviceaccount.com";

	static final String OTHER = "realestate-scraper-other@test.iam.gserviceaccount.com";

	private static final RSAKey KEY = rsaKey();

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ListingRepository listings;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserRepository users;

	@Test
	void ingestWithoutTokenIsUnauthorized() {
		assertThat(ingest(listing(newId()))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ingestWithAnAdminTokenIsUnauthorized() {
		User admin = this.users
			.save(new User("Admin", "admin-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN, "unused"));
		assertThat(ingest(listing(newId())).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + this.tokenService.createAccessToken(admin)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ingestWithAnyClaimWrongIsUnauthorized() {
		List<Consumer<JwtClaimsSet.Builder>> wrongs = List.of((claims) -> claims.audience(List.of("https://other.test")),
				(claims) -> claims.claim("email", "someone-else@test.iam.gserviceaccount.com"),
				(claims) -> claims.claim("email_verified", false), (claims) -> claims.issuer("https://evil.test"),
				(claims) -> claims.issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)));
		for (Consumer<JwtClaimsSet.Builder> wrong : wrongs) {
			assertThat(ingest(listing(newId())).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ, wrong)))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	@Test
	void scraperTokensDontWorkOnOtherEndpoints() {
		assertThat(this.mvc.get()
			.uri("/api/v1/admin/listings?publishedStatus=PENDING")
			.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ingestStoresAPendingListingOfTheCallersSourceWithItsImages() throws Exception {
		String id = newId();
		MvcTestResult result = ingest(listing(id)).file(image()).file(image())
			.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status":"CREATED","listing":{"publishedStatus":"PENDING","type":"RENT","title":"Villa %s",
					 "price":45000000,"bedrooms":3,"bathrooms":2,"areaSqm":180.5,"source":"fazwaz",
					 "sourceUrl":"https://www.fazwaz.vn/property-rent/villa-u%s"}}""".formatted(id, id));
		String listingId = JsonPath.read(result.getResponse().getContentAsString(), "$.listing.id");
		assertThat(result).headers().hasValue(HttpHeaders.LOCATION, "/api/v1/admin/listings/" + listingId);
		List<String> imgUrls = JsonPath.read(result.getResponse().getContentAsString(), "$.listing.imgUrls");
		assertThat(imgUrls).hasSize(2);
		assertThat(this.mvc.get().uri(imgUrls.getFirst())).hasStatus(HttpStatus.OK);
		// Waits for an admin.
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings/{id}", listingId)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void ingestOfAKnownListingIsSkipped() {
		String id = newId();
		assertThat(ingest(listing(id)).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ)))
			.hasStatus(HttpStatus.CREATED);

		assertThat(ingest(listing(id)).file(image()).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status":"SKIPPED","listing":null}""");
		// Same ID on another website: a different listing.
		assertThat(ingest(listing(id)).header(HttpHeaders.AUTHORIZATION, bearer(OTHER)))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void ingestOfARejectedListingIsSkipped() throws Exception {
		String id = newId();
		MvcTestResult created = ingest(listing(id)).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ)).exchange();
		String listingId = JsonPath.read(created.getResponse().getContentAsString(), "$.listing.id");
		User admin = this.users
			.save(new User("Admin", "admin-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN, "unused"));
		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings/{id}/reject", listingId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.tokenService.createAccessToken(admin)))
			.hasStatus(HttpStatus.OK);

		assertThat(ingest(listing(id)).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))).bodyJson()
			.isLenientlyEqualTo("""
					{"status":"SKIPPED"}""");
	}

	@Test
	void ingestWithANonImageStoresNothing() {
		String id = newId();
		assertThat(ingest(listing(id)).file(image())
			.file(new MockMultipartFile("images", "a.gif", "image/gif", "GIF89a....".getBytes()))
			.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

		assertThat(this.listings.existsBySourceNameAndSourceExternalId("fazwaz", id)).isFalse();
	}

	@Test
	void ingestWithTooManyImagesIsBadRequest() {
		MockMultipartMvcRequestBuilder request = ingest(listing(newId()));
		for (int i = 0; i <= ScraperDtos.MAX_IMAGES; i++) {
			request.file(image());
		}
		assertThat(request.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void ingestOfAnInvalidListingIsBadRequest() {
		assertThat(ingest("""
				{"sourceId":"","sourceUrl":"not a url","type":"RENT","title":"","price":1.5}""")
			.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownIdsAreThoseTheCallersSourceHasNotSent() {
		String known = newId();
		String knownElsewhere = newId();
		String unknown = newId();
		ingest(listing(known)).header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ)).exchange();
		ingest(listing(knownElsewhere)).header(HttpHeaders.AUTHORIZATION, bearer(OTHER)).exchange();

		assertThat(this.mvc.post()
			.uri("/api/v1/scraper/listings/unknown-ids")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceIds":["%s","%s","%s"]}""".formatted(known, knownElsewhere, unknown))
			.header(HttpHeaders.AUTHORIZATION, bearer(FAZWAZ)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isEqualTo("""
					{"sourceIds":["%s","%s"]}""".formatted(knownElsewhere, unknown));
	}

	private MockMultipartMvcRequestBuilder ingest(String listingJson) {
		return this.mvc.post()
			.uri("/api/v1/scraper/listings")
			.multipart()
			.file(new MockMultipartFile("listing", "", MediaType.APPLICATION_JSON_VALUE, listingJson.getBytes()));
	}

	private static String listing(String id) {
		return """
				{"sourceId":"%s","sourceUrl":"https://www.fazwaz.vn/property-rent/villa-u%s","type":"RENT",
				 "title":"Villa %s","description":"Pool and garden","price":45000000,"bedrooms":3,"bathrooms":2,
				 "areaSqm":180.5}""".formatted(id, id, id);
	}

	private static MockMultipartFile image() {
		return new MockMultipartFile("images", "photo.jpg", "image/jpeg", JPEG);
	}

	private static String newId() {
		return UUID.randomUUID().toString();
	}

	private static String bearer(String email) {
		return bearer(email, (claims) -> {
		});
	}

	/** A Google-like ID token for the service account {@code email}, then changed by {@code change}. */
	private static String bearer(String email, Consumer<JwtClaimsSet.Builder> change) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("https://accounts.google.com")
			.audience(List.of(AUDIENCE))
			.subject("1234567890")
			.issuedAt(now)
			.expiresAt(now.plusSeconds(3600))
			.claim("email", email)
			.claim("email_verified", true);
		change.accept(claims);
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
		return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static RSAKey rsaKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			var keyPair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyID("test")
				.build();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration
	static class TestKey {

		@Bean
		@Primary
		ScraperTokenDecoder testScraperTokenDecoder(ScraperProperties properties) throws Exception {
			return new ScraperTokenDecoder(NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build(), properties);
		}

	}

}
