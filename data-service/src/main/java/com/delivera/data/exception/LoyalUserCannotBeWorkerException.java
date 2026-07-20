package com.delivera.data.exception;

public class LoyalUserCannotBeWorkerException extends RuntimeException {
    public LoyalUserCannotBeWorkerException() {
        super("This email belongs to a loyal user and cannot be invited as a worker");
    }
}
