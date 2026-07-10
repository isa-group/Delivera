package com.delivera.data.org.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.data.org.dto.OrgCheckRequest;

import reactor.core.publisher.Mono;

@Service
public class OrgClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public OrgClient(SmartMicroserviceClient client) {
        this.client = client;
    }

     public  Mono<Boolean> checkOrgData(OrgCheckRequest requestBody) {
        return client.request()
        .service("delivera-service")
        .path("/internal/organization/check")
        .method(HttpMethod.GET)
        .mtls()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(Boolean.class);

    }

}
