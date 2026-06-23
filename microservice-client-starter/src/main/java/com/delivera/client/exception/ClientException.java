package com.delivera.client.exception;

public class ClientException extends ApiException {
    public ClientException(int status, String body) {
        super(status, body);
    }
}
