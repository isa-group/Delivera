package com.delivera.fms.routing.service;

import com.delivera.fms.routing.config.EngineUnavailableException;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Service
public class EngineDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EngineDispatcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    private final Map<TypeSolver, WebClient> engineClients;

    public EngineDispatcher(
            @Qualifier("greedyWebClient") WebClient greedyWebClient,
            @Qualifier("randomWebClient") WebClient randomWebClient,
            @Qualifier("geneticWebClient") WebClient geneticWebClient) {
        this.engineClients = Map.of(
                TypeSolver.GREEDY, greedyWebClient,
                TypeSolver.RANDOM, randomWebClient,
                TypeSolver.GENETIC, geneticWebClient
        );
    }

    public RoutingResponse dispatch(RoutingRequest request) {
        TypeSolver solverType = request.solverType();
        WebClient client = engineClients.get(solverType);

        if (client == null) {
            throw new IllegalArgumentException("No engine configured for solver type: " + solverType);
        }

        log.info("Dispatching problem '{}' to {} engine", request.problemId(), solverType);

        try {
            RoutingResponse response = client.post()
                    .uri("/api/v1/engine/solve")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RoutingResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                throw new EngineUnavailableException("Engine " + solverType + " returned empty response");
            }

            log.info("Problem '{}' solved by {} engine. Total cost: {}",
                    request.problemId(), solverType, response.totalCost());
            return response;

        } catch (WebClientResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EngineUnavailableException(
                    "Failed to communicate with " + solverType + " engine: " + ex.getMessage(), ex);
        }
    }
}
