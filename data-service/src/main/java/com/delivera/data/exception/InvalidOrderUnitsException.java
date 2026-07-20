package com.delivera.data.exception;

public class InvalidOrderUnitsException extends RuntimeException {
    public InvalidOrderUnitsException() {
        super("Order units are invalid or do not belong to the company");
    }
}
