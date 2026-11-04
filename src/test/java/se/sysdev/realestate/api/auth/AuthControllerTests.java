package se.sysdev.realestate.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	static final String ADMIN_SIGNIN = """
			{"email":"rikard.virta@sysdev.se","password":"HelloWorld@123"}""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenService tokenService;

	@Test
	void signInWithSeededAdminReturnsBothTokens() throws Exception {
		MvcTestResult result = signIn(ADMIN_SIGNIN);
		assertThat(result).hasStatus(HttpStatus.OK);
		String body = result.getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.accessToken");
		String refreshToken = JsonPath.read(body, "$.refreshToken");
		assertThat(this.tokenService.decodeAccessToken(accessToken).getClaimAsStringList(TokenService.ROLES_CLAIM))
			.containsExactly("ADMIN");
		assertThat(this.tokenService.decodeRefreshToken(refreshToken).getSubject()).isNotBlank();
	}

	@Test
	void signInIsCaseInsensitiveOnEmail() {
		assertThat(signIn("""
				{"email":"Rikard.Virta@SYSDEV.se","password":"HelloWorld@123"}""")).hasStatus(HttpStatus.OK);
	}

	@Test
	void signInWithWrongPasswordIsUnauthorized() {
		assertThat(signIn("""
				{"email":"rikard.virta@sysdev.se","password":"wrong"}""")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signInWithUnknownEmailIsUnauthorized() {
		assertThat(signIn("""
				{"email":"nobody@sysdev.se","password":"HelloWorld@123"}""")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signInWithInvalidBodyIsBadRequest() {
		assertThat(signIn("""
				{"email":"not-an-email","password":""}""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void refreshReturnsNewAccessToken() throws Exception {
		String refreshToken = JsonPath.read(signIn(ADMIN_SIGNIN).getResponse().getContentAsString(),
				"$.refreshToken");
		MvcTestResult result = refresh(refreshToken);
		assertThat(result).hasStatus(HttpStatus.OK).bodyJson().hasPath("$.accessToken");
		String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
		assertThat(this.tokenService.decodeAccessToken(accessToken).getSubject()).isNotBlank();
	}

	@Test
	void refreshWithAccessTokenIsUnauthorized() throws Exception {
		String accessToken = JsonPath.read(signIn(ADMIN_SIGNIN).getResponse().getContentAsString(),
				"$.accessToken");
		assertThat(refresh(accessToken)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWithGarbageIsUnauthorized() {
		assertThat(refresh("not.a.jwt")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult signIn(String body) {
		return this.mvc.post()
			.uri("/api/v1/auth/signin")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult refresh(String refreshToken) {
		return this.mvc.post()
			.uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}")
			.exchange();
	}

}
