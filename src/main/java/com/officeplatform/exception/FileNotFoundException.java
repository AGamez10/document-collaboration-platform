package com.officeplatform.exception;

public class FileNotFoundException extends RuntimeException {

    private final Long fileId;

    public FileNotFoundException(Long fileId) {
        super("El archivo con ID " + fileId + " no existe o no pertenece a tu proyecto");
        this.fileId = fileId;
    }

    public Long getFileId() {
        return fileId;
    }

}
