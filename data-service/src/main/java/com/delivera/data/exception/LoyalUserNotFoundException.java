package com.delivera.data.exception;

public class LoyalUserNotFoundException extends RuntimeException {
    public LoyalUserNotFoundException() {
        super("Loyal user not found");
    }
}
