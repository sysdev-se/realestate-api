package se.sysdev.realestate.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.security.SecurityConfig;

@WebMvcTest(HelloController.class)
@Import({ SecurityConfig.class, TokenService.class })
class HelloControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void helloIsPublicAndReturnsWorld() {
		assertThat(this.mvc.get().uri("/hello"))
			.hasStatus(HttpStatus.OK)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.bodyJson()
			.isLenientlyEqualTo("{\"message\":\"world\"}");
	}

}
