package com.officeplatform.exception;

public class OnlyOfficeException extends RuntimeException {

    public OnlyOfficeException(String message) {
        super(message);
    }

    public OnlyOfficeException(String message, Throwable cause) {
        super(message, cause);
    }

}
