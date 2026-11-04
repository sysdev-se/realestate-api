package se.sysdev.realestate.api.image;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Images in a private Cloud Storage bucket, through the JSON API. Authenticated as the runtime
 * service account (realestate-infrastructure grants it storage.objectUser on the bucket).
 */
class GcsImageStore implements ImageStore {
	static final String BASE_URL = "https://storage.googleapis.com";
	private final RestClient restClient;
	private final String bucket;
	private final Supplier<String> accessToken;

	GcsImageStore(RestClient restClient, String bucket, Supplier<String> accessToken) {
		this.restClient = restClient;
		this.bucket = bucket;
		this.accessToken = accessToken;
	}

	@Override
	public void put(String name, String contentType, byte[] content) {
		this.restClient.post()
			.uri("/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}", this.bucket, name)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.parseMediaType(contentType))
			.body(content)
			.retrieve()
			.toBodilessEntity();
	}

	@Override
	public Optional<StoredImage> get(String name) {
		return this.restClient.get()
			.uri("/storage/v1/b/{bucket}/o/{name}?alt=media", this.bucket, name)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.exchange((request, response) -> {
				if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
					return Optional.empty();
				}
				if (response.getStatusCode().isError()) {
					throw new IllegalStateException("Cloud Storage GET " + name + ": " + response.getStatusCode());
				}
				MediaType contentType = response.getHeaders().getContentType();
				return Optional.of(new StoredImage(
						(contentType != null) ? contentType.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
						response.getBody().readAllBytes()));
			});
	}

	@Override
	public void delete(String name) {
		this.restClient.delete()
			.uri("/storage/v1/b/{bucket}/o/{name}", this.bucket, name)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.exchange((request, response) -> {
				if (response.getStatusCode().isError()
						&& !response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
					throw new IllegalStateException("Cloud Storage DELETE " + name + ": " + response.getStatusCode());
				}
				return null;
			});
	}

	private String bearer() {
		return "Bearer " + this.accessToken.get();
	}
}
