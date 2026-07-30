package com.delivera.worker.service;

import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnitWorkerClient {

    private final SmartMicroserviceClient client;
    private final Integer RETRIRES = 3;
    private final Integer TIMEOUT = 5000;

     public void unassignWorkerOfAllUnits(UUID workerId) {
        client.request()
        .service("data-service")
        .path("/internal/units/unassign/"+workerId)
        .method(HttpMethod.DELETE)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }
}

