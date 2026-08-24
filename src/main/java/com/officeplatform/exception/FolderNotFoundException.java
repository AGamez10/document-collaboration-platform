package com.officeplatform.exception;

public class FolderNotFoundException extends RuntimeException {

    private final Long folderId;

    public FolderNotFoundException(Long folderId) {
        super("La carpeta con ID " + folderId + " no existe o no pertenece a tu proyecto");
        this.folderId = folderId;
    }

    public Long getFolderId() {
        return folderId;
    }

}
