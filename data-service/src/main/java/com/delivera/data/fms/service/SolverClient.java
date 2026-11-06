package com.delivera.data.fms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.data.fms.dto.solver.info.SolverCatalog;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolverClient {

    @Value("${app.fms.routing.base-url}")
    private String fmsRoutingBaseUrl;

    private final SmartMicroserviceClient client;

    private final Integer SOLVER_TIMEOUT = 30_000;

    private final Integer INFO_TIMEOUT = 5_000;

    private final Integer RETRIES = 3;


    public SolverCatalog getSolverCatalog(boolean includeStatus) {
        return client.request()
        .url(fmsRoutingBaseUrl +"/api/v1/fms/solvers?includeStatus="+includeStatus)
        .method(HttpMethod.GET)
        .http()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIES)
        .timeout(INFO_TIMEOUT)
        .log()
        .executeBasicRequest(SolverCatalog.class)
        .block();
    
    }
    
      


}
