package com.delivera.data.exception;

public class SubscriptionLimitException extends RuntimeException {

    private final String resource;

    public SubscriptionLimitException(String resource) {
        super("Subscription limit reached for: " + resource);
        this.resource = resource;
    }

    public String getCode() {
        return "SUBSCRIPTION_LIMIT_" + resource.toUpperCase();
    }
}
