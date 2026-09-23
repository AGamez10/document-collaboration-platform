package com.officeplatform.storage.provider;

import java.io.InputStream;

public interface StorageProvider {

    void upload(String bucket, String objectName, InputStream inputStream, long size, String contentType);

    InputStream download(String bucket, String objectName);

    void delete(String bucket, String objectName);

    String presignedDownloadUrl(String bucket, String objectName, int expirySeconds);

    /**
     * Nombres de todos los objetos del bucket.
     *
     * <p>Existe para el chequeo de paridad: sin poder enumerar el almacenamiento, la unica forma de
     * saber si una fila tiene binario es pedirlo uno por uno, y sobre miles de archivos eso son
     * miles de viajes de red.
     */
    java.util.List<String> listObjectNames(String bucket);

}
