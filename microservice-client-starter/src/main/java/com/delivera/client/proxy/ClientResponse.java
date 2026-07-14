package com.delivera.client.proxy;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientResponse<R> {

    private int status;
    private Map<String, String> headers;
    private R body;

    public ClientResponse(int status, Map<String, String> headers, R body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

   
}
