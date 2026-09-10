package com.officeplatform.exception;

/**
 * El proyecto alcanzó su tope de almacenamiento.
 *
 * <p>Separada de {@link StorageException} porque no es un fallo del almacenamiento sino una
 * decisión administrativa, y merece su propio código HTTP: 413 le dice al cliente que el problema
 * es el tamaño y no un error del servidor que convenga reintentar.
 */
public class StorageQuotaExceededException extends RuntimeException {

    public StorageQuotaExceededException(String message) {
        super(message);
    }
}
