package com.delivera.org.service;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.org.dto.CompanySettingsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsClient {

    private final SmartMicroserviceClient client;

    public void createSeed(CompanySettingsDTO requestBody, String url) {
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
