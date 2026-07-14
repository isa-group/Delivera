package com.delivera.data.exception;

public class WorkerNotFoundException extends RuntimeException {
    public WorkerNotFoundException() {
        super("Worker not found");
    }
}
