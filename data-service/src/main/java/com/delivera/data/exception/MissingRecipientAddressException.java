package com.delivera.data.exception;

public class MissingRecipientAddressException extends RuntimeException {
    public MissingRecipientAddressException() {
        super("Recipient address is required for B2C orders");
    }
}
