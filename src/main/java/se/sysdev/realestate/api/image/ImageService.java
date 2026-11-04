package se.sysdev.realestate.api.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import se.sysdev.realestate.api.image.ImageStore.StoredImage;

@Service
public class ImageService {
	public static final String PATH = "/api/v1/consumer/images/";
	private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
	private static final Pattern NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
	private final ImageStore store;

	public ImageService(ImageStore store) {
		this.store = store;
	}

	public String upload(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
		}
		byte[] content = bytes(file);
		ImageType type = ImageType.detect(content)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"Only JPEG, PNG and WebP images are supported"));
		String name = UUID.randomUUID() + "." + type.extension();
		this.store.put(name, type.contentType(), content);
		return name;
	}

	public Optional<StoredImage> find(String name) {
		return NAME.matcher(name).matches() ? this.store.get(name) : Optional.empty();
	}

	public void deleteAll(Collection<String> urls) {
		for (String url : urls) {
			nameOf(url).ifPresent((name) -> {
				try {
					this.store.delete(name);
				}
				catch (RuntimeException ex) {
					logger.warn("Could not delete image {}", name, ex);
				}
			});
		}
	}

	static Optional<String> nameOf(String url) {
		String path;
		try {
			path = URI.create(url).getPath();
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (path == null || !path.startsWith(PATH)) {
			return Optional.empty();
		}
		String name = path.substring(PATH.length());
		return NAME.matcher(name).matches() ? Optional.of(name) : Optional.empty();
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
