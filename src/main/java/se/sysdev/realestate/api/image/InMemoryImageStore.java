package se.sysdev.realestate.api.image;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Used when no bucket is configured (local runs, tests). Images are lost on restart. */
class InMemoryImageStore implements ImageStore {

	private final Map<String, StoredImage> images = new ConcurrentHashMap<>();

	@Override
	public void put(String name, String contentType, byte[] content) {
		this.images.put(name, new StoredImage(contentType, content));
	}

	@Override
	public Optional<StoredImage> get(String name) {
		return Optional.ofNullable(this.images.get(name));
	}

	@Override
	public void delete(String name) {
		this.images.remove(name);
	}

}
