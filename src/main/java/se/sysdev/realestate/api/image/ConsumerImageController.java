package se.sysdev.realestate.api.image;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/consumer/images")
public class ConsumerImageController {
	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
	private final ImageService imageService;

	public ConsumerImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@GetMapping("/{name}")
	public ResponseEntity<byte[]> get(@PathVariable String name) {
		return this.imageService.find(name)
			.map((image) -> ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CACHE)
				.body(image.content()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image " + name + " not found"));
	}

}
