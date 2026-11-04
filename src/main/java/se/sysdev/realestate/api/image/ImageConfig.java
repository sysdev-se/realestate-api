package se.sysdev.realestate.api.image;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

// AOT only scans beans for this, and the token provider isn't one: the native image needs it
// to read the metadata server's JSON.
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(MetadataServerTokenProvider.TokenResponse.class)
class ImageConfig {
	private static final Logger logger = LoggerFactory.getLogger(ImageConfig.class);
	// Decided inside the method, not with @ConditionalOnProperty: the native image fixes
	// conditions at build time, but IMAGE_BUCKET is only known at runtime.
	@Bean
	ImageStore imageStore(@Value("${app.images.bucket:}") String bucket) {
		if (!StringUtils.hasText(bucket)) {
			logger.warn("IMAGE_BUCKET is not set; images are kept in memory and lost on restart");
			return new InMemoryImageStore();
		}
		MetadataServerTokenProvider tokens = new MetadataServerTokenProvider(
				RestClient.builder().baseUrl(MetadataServerTokenProvider.BASE_URL).build(), Clock.systemUTC());
		return new GcsImageStore(RestClient.builder().baseUrl(GcsImageStore.BASE_URL).build(), bucket, tokens::token);
	}

}
