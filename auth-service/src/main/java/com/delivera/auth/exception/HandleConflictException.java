package com.delivera.auth.exception;

public class HandleConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HandleConflictException(String handle, Throwable cause) {
        super("Could not generate a unique handle for: " + handle, cause);
    }
}
