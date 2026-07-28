package com.delivera.vehicle.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.fms.dto.VehicleDto;
import com.delivera.vehicle.dto.VehicleRequest;

@Service
public class VehicleClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public VehicleClient(SmartMicroserviceClient client) {
        this.client = client;
    }


    public Void createSeed(VehicleRequest requestBody,  String url) {
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
        .executeBasicRequest(Void.class).block();
    }

    public List<VehicleDto> getAllByCompanyId(UUID companyId) {
        return client.request()
        .service("data-service")
        .path("/internal/vehicles/companies/"+companyId)
        .method(HttpMethod.GET)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(
            new ParameterizedTypeReference<List<VehicleDto>>() {})
        .block();
    }


}
