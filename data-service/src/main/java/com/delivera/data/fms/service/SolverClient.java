package com.delivera.data.fms.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.data.fms.dto.solver.info.TypeSolver;
import com.delivera.data.fms.dto.solver.info.SolverCatalog;
import com.delivera.data.fms.dto.solver.request.RoutingRequest;
import com.delivera.data.fms.dto.solver.request.RoutingResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolverClient {

    @Value("${app.fms.routing.base-url}")
    private String fmsRoutingBaseUrl;

    private final SmartMicroserviceClient client;

    private final Integer SOLVER_TIMEOUT = 150_000;

    private final Integer QUANTUM_TIMEOUT = 300_000;

    private final Integer INFO_TIMEOUT = 5_000;

    private final Set<TypeSolver> specialSolvers = Set.of(TypeSolver.DWAVE, TypeSolver.QISKIT);

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

    public RoutingResponse solve(RoutingRequest request) {
        Integer timeout = getSolverTimeout(request);
        return client.request()
        .url(fmsRoutingBaseUrl+"/api/v1/fms/routing/solve")
        .method(HttpMethod.POST)
        .body(request)
        .http()
        .failOn4xx(false)
        .failOn5xx(false)
        .retry(RETRIES)
        .timeout(timeout)
        .log()
        .executeBasicRequest(RoutingResponse.class)
        .block();
    }
    

    private Integer getSolverTimeout(RoutingRequest request) {
        TypeSolver solver = request.solverType();
        // TODO: WE REQUIRED PARAMS

        if (specialSolvers.contains(solver)) {
            return QUANTUM_TIMEOUT;
        }
        return SOLVER_TIMEOUT;
    }
      


}
