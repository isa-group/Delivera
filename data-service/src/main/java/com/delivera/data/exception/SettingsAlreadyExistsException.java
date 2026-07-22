package com.delivera.data.exception;

import java.util.UUID;

public class SettingsAlreadyExistsException extends RuntimeException {
    public SettingsAlreadyExistsException(UUID id) {
        super("Company settings already exists: " + id);
    }
}


