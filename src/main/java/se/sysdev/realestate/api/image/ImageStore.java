package se.sysdev.realestate.api.image;

import java.util.Optional;

/** Where image files live: Cloud Storage on Cloud Run, memory otherwise. */
public interface ImageStore {

	void put(String name, String contentType, byte[] content);

	Optional<StoredImage> get(String name);

	void delete(String name);

	record StoredImage(String contentType, byte[] content) {
	}

}
