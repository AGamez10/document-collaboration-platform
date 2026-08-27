package com.officeplatform.exception;

/**
 * Raised when an upload is rejected because of its type.
 *
 * <p>Kept apart from {@link StorageException}, which maps to 500: rejecting a file type is a
 * client error, and answering 500 made a correct validation look like a server failure.
 * Translated to 415 Unsupported Media Type by {@code GlobalExceptionHandler}.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }

}
