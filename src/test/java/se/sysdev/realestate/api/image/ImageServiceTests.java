package se.sysdev.realestate.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ImageServiceTests {

	private static final String NAME = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg";

	@Test
	void nameOfAcceptsOwnImageUrlsOnAnyHost() {
		assertThat(ImageService.nameOf("https://realestate-api-1.asia-southeast1.run.app/api/v1/consumer/images/" + NAME))
			.contains(NAME);
		assertThat(ImageService.nameOf("http://localhost:8080/api/v1/consumer/images/" + NAME)).contains(NAME);
	}

	@Test
	void nameOfIgnoresOtherUrls() {
		assertThat(ImageService.nameOf("https://picsum.photos/seed/cottage-1/800/600")).isEmpty();
		assertThat(ImageService.nameOf("https://example.com/api/v1/consumer/images/other.jpg")).isEmpty();
		assertThat(ImageService.nameOf("https://example.com/api/v1/consumer/images/../secret/" + NAME)).isEmpty();
		assertThat(ImageService.nameOf("not a url")).isEmpty();
	}

	@Test
	void deleteAllDeletesOwnImagesAndKeepsGoingAfterAFailure() {
		InMemoryImageStore store = new InMemoryImageStore() {
			@Override
			public void delete(String name) {
				if (name.startsWith("00000000")) {
					throw new IllegalStateException("boom");
				}
				super.delete(name);
			}
		};
		String failing = "00000000-0000-4000-8000-000000000000.png";
		store.put(NAME, "image/jpeg", new byte[] { 1 });
		store.put(failing, "image/png", new byte[] { 2 });

		new ImageService(store).deleteAll(List.of("https://x/api/v1/consumer/images/" + failing,
				"https://x/api/v1/consumer/images/" + NAME, "https://picsum.photos/1"));

		assertThat(store.get(NAME)).isEmpty();
		assertThat(store.get(failing)).isPresent();
	}

}
