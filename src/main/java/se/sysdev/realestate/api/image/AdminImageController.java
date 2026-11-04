package se.sysdev.realestate.api.image;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import se.sysdev.realestate.api.image.ImageDtos.ImageResponse;

// Access to /api/v1/admin/** (ADMIN only) is enforced in SecurityConfig.
@RestController
@RequestMapping("/api/v1/admin/images")
public class AdminImageController {

	private final ImageService imageService;

	public AdminImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	/** Uploads one image; put the returned URL in a listing's imgUrls. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ImageResponse> upload(@RequestParam("file") MultipartFile file) {
		String name = this.imageService.upload(file);
		URI url = ServletUriComponentsBuilder.fromCurrentContextPath().path(ImageService.PATH + name).build().toUri();
		return ResponseEntity.created(url).body(new ImageResponse(url.toString()));
	}

}
