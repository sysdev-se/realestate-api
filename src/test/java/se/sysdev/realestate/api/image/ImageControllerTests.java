package se.sysdev.realestate.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMultipartMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import se.sysdev.realestate.api.auth.TokenService;
import se.sysdev.realestate.api.user.Role;
import se.sysdev.realestate.api.user.User;
import se.sysdev.realestate.api.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ImageControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserRepository users;

	@Test
	void uploadedImageIsServedWithItsDetectedTypeAndLongCaching() throws Exception {
		// The client's name and type are ignored: the bytes are a PNG.
		MvcTestResult result = upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", ImageTypeTests.PNG))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		String url = JsonPath.read(result.getResponse().getContentAsString(), "$.url");
		assertThat(url).matches("http://localhost/api/v1/consumer/images/[0-9a-f-]{36}\\.png");
		assertThat(result).headers().hasValue(HttpHeaders.LOCATION, url);

		MvcTestResult image = this.mvc.get().uri(url).exchange();
		assertThat(image).hasStatus(HttpStatus.OK).hasContentType("image/png");
		assertThat(image).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable");
		assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(ImageTypeTests.PNG);
	}

	@Test
	void uploadOfANonImageIsUnsupportedMediaType() {
		assertThat(upload(new MockMultipartFile("file", "a.gif", "image/gif", "GIF89a....".getBytes()))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void uploadOfAnEmptyOrMissingFileIsBadRequest() {
		assertThat(upload(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(upload(new MockMultipartFile("other", "a.jpg", "image/jpeg", ImageTypeTests.JPEG))
			.header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRequiresAnAdmin() {
		MockMultipartFile jpeg = new MockMultipartFile("file", "a.jpg", "image/jpeg", ImageTypeTests.JPEG);
		assertThat(upload(jpeg)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(upload(jpeg).header(HttpHeaders.AUTHORIZATION, bearer(Role.CONSUMER)))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void unknownOrMalformedImageNamesAreNotFound() {
		assertThat(this.mvc.get().uri("/api/v1/consumer/images/{name}", UUID.randomUUID() + ".jpg"))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/api/v1/consumer/images/whatever.jpg")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MockMultipartMvcRequestBuilder upload(MockMultipartFile file) {
		return this.mvc.post().uri("/api/v1/admin/images").multipart().file(file);
	}

	private String bearer(Role role) {
		return "Bearer " + this.tokenService.createAccessToken(user(role));
	}

	private User user(Role role) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
		return this.users.save(new User("Test " + role, email, null, role, "unused"));
	}

}
