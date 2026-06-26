package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.client.core.SmartMicroserviceClient;

import reactor.core.publisher.Mono;

@Service
public class DeliveraClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public DeliveraClient(SmartMicroserviceClient client) {
        this.client = client;
    }

    
    
    public Mono<DeliveraOrgContext> getOrgInfo(UUID userId) {
        return client.request()
        .service("delivera-service")
        .path("/internal/auth/context/"+userId)
        .method(HttpMethod.GET)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .log()
        .executeBasicRequest(DeliveraOrgContext.class);

    } 


    

}
