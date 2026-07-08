package com.delivera.data.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Cannot remove or change role of the last company admin");
    }
}
