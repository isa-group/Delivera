package com.delivera.fms.routing.service;

import com.delivera.fms.routing.config.EngineUnavailableException;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Service
public class EngineDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EngineDispatcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    private final SolverRegistry registry;

    public EngineDispatcher(SolverRegistry registry) {
        this.registry = registry;
    }

    /**
     * Envia el problema al motor del solver indicado, con los parametros ya
     * resueltos contra sus metadatos.
     *
     * La resolucion de parametros vive aqui y no en el controlador porque es un
     * invariante del despacho: cualquier via de entrada (peticion directa o carga
     * de instancia) llega al motor con la configuracion completa.
     */
    public RoutingResponse dispatch(RoutingRequest request) {
        TypeSolver solverType = request.solverType();
        WebClient client = registry.clientFor(solverType);

        Map<String, Object> parameters = registry.resolveParameters(solverType, request.parameters());
        RoutingRequest enriched = request.withParameters(parameters);

        log.info("Dispatching problem '{}' to {} engine with parameters {}",
                request.problemId(), solverType, parameters);

        try {
            RoutingResponse response = client.post()
                    .uri("/api/v1/engine/solve")
                    .bodyValue(enriched)
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
