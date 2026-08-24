package com.officeplatform.service.storage;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.officeplatform.storage.provider.StorageProvider;

@Service
public class StorageServiceImpl implements StorageService {

    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 900;

    private final StorageProvider storageProvider;
    private final String bucket;

    public StorageServiceImpl(
            StorageProvider storageProvider,
            @Value("${office-platform.storage.minio.bucket}") String bucket) {
        this.storageProvider = storageProvider;
        this.bucket = bucket;
    }

    @Override
    public void store(String objectName, InputStream content, long size, String contentType) {
        storageProvider.upload(bucket, objectName, content, size, contentType);
    }

    @Override
    public InputStream retrieve(String objectName) {
        return storageProvider.download(bucket, objectName);
    }

    @Override
    public void delete(String objectName) {
        storageProvider.delete(bucket, objectName);
    }

    @Override
    public String presignedDownloadUrl(String objectName) {
        return storageProvider.presignedDownloadUrl(bucket, objectName, PRESIGNED_URL_EXPIRY_SECONDS);
    }

}
