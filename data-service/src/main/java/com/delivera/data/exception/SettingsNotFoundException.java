package com.delivera.data.exception;

import java.util.UUID;

public class SettingsNotFoundException extends RuntimeException {
    public SettingsNotFoundException(UUID id) {
        super("Company settings not found: " + id);
    }
}


