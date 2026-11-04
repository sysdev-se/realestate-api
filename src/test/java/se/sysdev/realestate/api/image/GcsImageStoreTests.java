package se.sysdev.realestate.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import se.sysdev.realestate.api.image.ImageStore.StoredImage;

class GcsImageStoreTests {

	private static final String NAME = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.png";

	private static final String OBJECT_URL = "https://storage.googleapis.com/storage/v1/b/images-bucket/o/" + NAME;

	private MockRestServiceServer server;

	private GcsImageStore store;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(GcsImageStore.BASE_URL);
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.store = new GcsImageStore(builder.build(), "images-bucket", () -> "test-token");
	}

	@Test
	void putUploadsTheBytesWithTheirContentType() {
		this.server
			.expect(requestTo("https://storage.googleapis.com/upload/storage/v1/b/images-bucket/o?uploadType=media&name="
					+ NAME))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
			.andExpect(header(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(content().bytes(new byte[] { 1, 2, 3 }))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		this.store.put(NAME, "image/png", new byte[] { 1, 2, 3 });
		this.server.verify();
	}

	@Test
	void getReturnsTheBytesAndContentType() {
		this.server.expect(requestTo(OBJECT_URL + "?alt=media"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
			.andRespond(withSuccess(new byte[] { 4, 5 }, MediaType.IMAGE_PNG));

		StoredImage image = this.store.get(NAME).orElseThrow();
		assertThat(image.contentType()).isEqualTo("image/png");
		assertThat(image.content()).containsExactly(4, 5);
	}

	@Test
	void getOfAMissingObjectIsEmpty() {
		this.server.expect(requestTo(OBJECT_URL + "?alt=media")).andRespond(withResourceNotFound());
		assertThat(this.store.get(NAME)).isEmpty();
	}

	@Test
	void getFailsOnOtherErrors() {
		this.server.expect(requestTo(OBJECT_URL + "?alt=media")).andRespond(withServerError());
		assertThatIllegalStateException().isThrownBy(() -> this.store.get(NAME));
	}

	@Test
	void deleteRemovesTheObjectAndIgnoresAMissingOne() {
		this.server.expect(requestTo(OBJECT_URL))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
			.andRespond(withNoContent());
		this.server.expect(requestTo(OBJECT_URL)).andRespond(withResourceNotFound());

		this.store.delete(NAME);
		this.store.delete(NAME);
		this.server.verify();
	}

	@Test
	void deleteFailsOnOtherErrors() {
		this.server.expect(requestTo(OBJECT_URL)).andRespond(withServerError());
		assertThatIllegalStateException().isThrownBy(() -> this.store.delete(NAME));
	}

}
