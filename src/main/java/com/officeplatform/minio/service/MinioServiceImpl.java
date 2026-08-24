package com.officeplatform.minio.service;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;

import com.officeplatform.exception.StorageException;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void upload(String bucket, String objectName, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1L)
                    .contentType(contentType)
                    .build());
        } catch (MinioException e) {
            throw new StorageException("No se pudo subir el archivo a MinIO: " + objectName, e);
        }
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (MinioException e) {
            throw new StorageException("No se pudo descargar el archivo de MinIO: " + objectName, e);
        }
    }

    @Override
    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (MinioException e) {
            throw new StorageException("No se pudo eliminar el archivo de MinIO: " + objectName, e);
        }
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build());
        } catch (MinioException e) {
            throw new StorageException("No se pudo generar la URL prefirmada para: " + objectName, e);
        }
    }

}
