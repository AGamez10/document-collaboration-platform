package com.officeplatform.exception;

public class SharePermissionNotFoundException extends RuntimeException {

    public SharePermissionNotFoundException(Long permissionId) {
        super("El permiso de compartición no existe: " + permissionId);
    }

}
