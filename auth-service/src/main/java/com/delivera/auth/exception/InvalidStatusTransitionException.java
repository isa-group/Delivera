package com.delivera.auth.exception;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException() {
        super("Invalid status transition");
    }
}
