package se.sysdev.realestate.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/hello")
	public HelloResponse hello() {
		return new HelloResponse("world");
	}

	public record HelloResponse(String message) {
	}

}
