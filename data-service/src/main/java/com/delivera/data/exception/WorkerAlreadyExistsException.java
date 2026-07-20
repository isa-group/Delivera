package com.delivera.data.exception;

public class WorkerAlreadyExistsException extends RuntimeException {
    public WorkerAlreadyExistsException() {
        super("Worker already exists in this company");
    }
}
