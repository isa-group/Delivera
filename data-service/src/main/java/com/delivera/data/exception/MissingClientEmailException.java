package com.delivera.data.exception;

public class MissingClientEmailException extends RuntimeException {
    public MissingClientEmailException() {
        super("Client email is required for B2C orders");
    }
}
