package com.delivera.client.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String body;

    public ApiException(int status, String body) {
        super("API error: " + status);
        this.status = status;
        this.body = body;
    }

}
