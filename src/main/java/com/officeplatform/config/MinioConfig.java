package com.officeplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;

import com.officeplatform.exception.StorageException;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${office-platform.storage.minio.endpoint}") String endpoint,
            @Value("${office-platform.storage.minio.access-key}") String accessKey,
            @Value("${office-platform.storage.minio.secret-key}") String secretKey,
            @Value("${office-platform.storage.minio.bucket}") String bucket) {

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucketExists(client, bucket);

        return client;
    }

    private void ensureBucketExists(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (MinioException e) {
            throw new StorageException("No se pudo verificar/crear el bucket de MinIO: " + bucket, e);
        }
    }

}
