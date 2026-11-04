package se.sysdev.realestate.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class CorsTests {

	private static final String WEB_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvcTester mvc;

	@Test
	void preflightFromAllowedOriginForAdminCreate() {
		assertThat(this.mvc.options()
			.uri("/api/v1/admin/listings")
			.header(HttpHeaders.ORIGIN, WEB_ORIGIN)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.hasStatus(HttpStatus.OK)
			.headers()
			.hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN)
			.hasValue(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
			.doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
	}

	@Test
	void preflightFromUnknownOriginIsForbidden() {
		assertThat(this.mvc.options()
			.uri("/api/v1/consumer/listings")
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.hasStatus(HttpStatus.FORBIDDEN)
			.headers()
			.doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
	}

	@Test
	void simpleRequestFromAllowedOriginGetsAllowOriginHeader() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings").header(HttpHeaders.ORIGIN, WEB_ORIGIN))
			.hasStatus(HttpStatus.OK)
			.headers()
			.hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN);
	}

	@Test
	void unauthorizedResponseStillCarriesCorsHeaderSoTheBrowserCanReadIt() {
		assertThat(this.mvc.post()
			.uri("/api/v1/admin/listings")
			.header(HttpHeaders.ORIGIN, WEB_ORIGIN)
			.contentType("application/json")
			.content("{}"))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.headers()
			.hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN)
			.hasValue(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location");
	}

	@Test
	void requestWithoutOriginIsUnaffected() {
		// What Postman and curl send: no Origin header, no CORS checks.
		assertThat(this.mvc.get().uri("/api/v1/consumer/listings"))
			.hasStatus(HttpStatus.OK)
			.headers()
			.doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
	}

}
