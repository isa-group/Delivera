package com.delivera.org.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.org.dto.CompanySettingsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsClient {

    private final SmartMicroserviceClient client;
    private final Integer RETRIRES = 3;
    private final Integer TIMEOUT = 5000;

    public void createSeed(CompanySettingsDTO requestBody, String url) {
        client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }

    public Void createSettings(CompanySettingsDTO requestBody) {
        return client.request()
        .service("data-service")
        .path("/internal/settings")
        .method(HttpMethod.POST)
        .mtls()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class)
        .block();
    }

    public void deleteAllByCompany(UUID companyId, Boolean confirmation) {
        client.request()
        .service("data-service")
        .path("/internal/settings/"+companyId)
        .method(HttpMethod.DELETE)
        .body(confirmation)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }

    public void deleteOrganization(Set<UUID> companyIds) {
        client.request()
        .service("data-service")
        .path("/internal/settings/organization")
        .method(HttpMethod.DELETE)
        .body(companyIds)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }

    public void cleanDataServiceDB(UUID adminCompanyId) {
        client.request()
        .service("data-service")
        .path("/internal/admin/data-service/"+adminCompanyId)
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
