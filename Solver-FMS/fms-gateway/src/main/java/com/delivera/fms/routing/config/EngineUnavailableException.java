package com.delivera.fms.routing.config;

public class EngineUnavailableException extends RuntimeException {

    public EngineUnavailableException(String message) {
        super(message);
    }

    public EngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
