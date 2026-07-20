package com.delivera.data.exception;

public class VehiclePlateConflictException extends RuntimeException {
    public VehiclePlateConflictException() {
        super("A vehicle with this plate already exists");
    }
}
