package com.officeplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.minio.MinioClient;

/**
 * Smoke test: the whole Spring context wires up.
 *
 * <p>Runs against the in-memory database configured in {@code src/test/resources/application.properties}.
 * The MinIO client is replaced by a mock because {@code MinioConfig} verifies (and creates) the
 * bucket while building the bean, which would otherwise require a reachable MinIO server.
 */
@SpringBootTest
class OfficePlatformApplicationTests {

	@MockitoBean
	private MinioClient minioClient;

	@Test
	void contextLoads() {
	}

}
