package com.delivera.exception;

public class WorkerCannotBeLoyalUserException extends RuntimeException {
    public WorkerCannotBeLoyalUserException() {
        super("This email belongs to a worker and cannot be used as a B2C recipient");
    }
}
