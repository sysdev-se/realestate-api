package se.sysdev.realestate.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageTypeTests {

	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };

	static final byte[] WEBP = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P' };

	@Test
	void detectsSupportedFormatsFromTheirFirstBytes() {
		assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
		assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
		assertThat(ImageType.detect(WEBP)).contains(ImageType.WEBP);
	}

	@Test
	void rejectsEverythingElse() {
		assertThat(ImageType.detect("GIF89a....".getBytes())).isEmpty();
		assertThat(ImageType.detect("RIFF1234WAVE".getBytes())).isEmpty();
		assertThat(ImageType.detect(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
		assertThat(ImageType.detect(new byte[0])).isEmpty();
	}

}
