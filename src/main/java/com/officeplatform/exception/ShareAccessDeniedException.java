package com.officeplatform.exception;

/** Raised when a caller tries to manage permissions of a resource they neither created nor admin. */
public class ShareAccessDeniedException extends RuntimeException {

    public ShareAccessDeniedException(String message) {
        super(message);
    }

}
