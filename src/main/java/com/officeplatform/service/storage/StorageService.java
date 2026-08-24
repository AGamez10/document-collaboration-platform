package com.officeplatform.service.storage;

import java.io.InputStream;

public interface StorageService {

    void store(String objectName, InputStream content, long size, String contentType);

    InputStream retrieve(String objectName);

    void delete(String objectName);

    String presignedDownloadUrl(String objectName);

}
