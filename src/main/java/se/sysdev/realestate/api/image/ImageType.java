package se.sysdev.realestate.api.image;

import java.util.Arrays;
import java.util.Optional;

/**
 * Image formats the api accepts. The format is detected from the file's first bytes, never from
 * its name or the client's content type.
 */
public enum ImageType {

	JPEG("jpg", "image/jpeg"),

	PNG("png", "image/png"),

	WEBP("webp", "image/webp");

	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final String extension;

	private final String contentType;

	ImageType(String extension, String contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	public String extension() {
		return this.extension;
	}

	public String contentType() {
		return this.contentType;
	}

	public static Optional<ImageType> detect(byte[] content) {
		if (startsWith(content, JPEG_MAGIC)) {
			return Optional.of(JPEG);
		}
		if (startsWith(content, PNG_MAGIC)) {
			return Optional.of(PNG);
		}
		// RIFF <4-byte size> WEBP
		if (content.length >= 12 && startsWith(content, "RIFF".getBytes())
				&& Arrays.equals(content, 8, 12, "WEBP".getBytes(), 0, 4)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
	}

}
