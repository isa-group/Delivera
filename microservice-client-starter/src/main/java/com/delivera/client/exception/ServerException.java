package com.delivera.client.exception;

public class ServerException extends ApiException {
    public ServerException(int status, String body) {
        super(status, body);
    }
}
