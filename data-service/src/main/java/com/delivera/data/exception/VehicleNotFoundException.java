package com.delivera.data.exception;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(java.util.UUID id) {
        super("Vehicle not found: " + id);
    }
}
