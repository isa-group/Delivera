package com.delivera.depot.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.depot.dto.AssignRequest;
import com.delivera.depot.dto.UnitRequest;

@Service
public class UnitClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public UnitClient(SmartMicroserviceClient client) {
        this.client = client;
    }


    public UUID createSeed(UnitRequest requestBody,  String url) {
        return client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(UUID.class).block();
    }


    public void assignSeed(AssignRequest requestBody, String url) {
         client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(Void.class).block();
    }


}
