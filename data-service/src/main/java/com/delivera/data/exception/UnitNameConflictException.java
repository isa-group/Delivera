package com.delivera.data.exception;

public class UnitNameConflictException extends RuntimeException {
    public UnitNameConflictException() {
        super("Unit name already exists in this company");
    }
}
