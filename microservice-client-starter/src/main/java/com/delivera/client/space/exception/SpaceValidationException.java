package com.delivera.client.space.exception;

import com.delivera.client.transaction.exception.CompensableValidationException;

public class SpaceValidationException extends CompensableValidationException {
    private final String resource;

    public SpaceValidationException(String resource) {
        super("Subscription limit reached for: " + resource);
        this.resource = resource;
    }

    public String getCode() {
        return "SUBSCRIPTION_LIMIT_" + resource.toUpperCase();
    }
}
