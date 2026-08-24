package com.officeplatform.storage.provider;

import java.io.InputStream;

public interface StorageProvider {

    void upload(String bucket, String objectName, InputStream inputStream, long size, String contentType);

    InputStream download(String bucket, String objectName);

    void delete(String bucket, String objectName);

    String presignedDownloadUrl(String bucket, String objectName, int expirySeconds);

}
