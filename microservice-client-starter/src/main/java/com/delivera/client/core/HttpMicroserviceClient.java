package com.delivera.client.core;


import org.springframework.web.reactive.function.client.WebClient;


public class HttpMicroserviceClient extends AbstractMicroserviceClient {

    public HttpMicroserviceClient(WebClient.Builder builder) {
        super(builder.build());
    }

    
}
