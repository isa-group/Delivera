package com.delivera.fms.routing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.fms.routing.config.EngineProperties;
import com.delivera.fms.routing.config.HealthCacheProperties;
import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import com.sun.net.httpserver.HttpServer;

/** Rutas de resolucion y de analisis declaradas por motor, contra un motor HTTP falso. */
class EngineDispatcherPathsTest {

    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private HttpServer server;
    private EngineDispatcher dispatcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        answer("/custom/solve", """
                {"problemId":"P","solverUsed":"HGS-ADC","totalCost":32.0,"computationTimeMs":12.5,"seed":42,
                 "routes":[{"vehicleId":"V1","depotId":"D1","stops":["C1"],"totalDistance":32.0,"totalLoad":20}]}""");
        answer("/custom/analyze", """
                {"instance_name":"P","analysis":{"is_viable":true,"features":{"capacity_tightness":0.2}}}""");
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort();
        EngineProperties.Engine custom = new EngineProperties.Engine();
        custom.setUrl(url);
        custom.setSolvePath("/custom/solve");
        custom.setAnalyzePath("/custom/analyze");
        EngineProperties.Engine plain = new EngineProperties.Engine();
        plain.setUrl(url);

        EngineProperties properties = new EngineProperties();
        properties.setEngines(Map.of(TypeSolver.HGS, custom, TypeSolver.GREEDY, plain));
        dispatcher = new EngineDispatcher(new SolverRegistry(properties, WebClient.builder(),
                new HealthStatusCache(new HealthCacheProperties())));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void solvesThroughTheEnginesOwnPath() {
        assertThat(dispatcher.dispatch(request(TypeSolver.HGS)).totalCost()).isEqualTo(32.0);
        assertThat(bodies).containsKey("/custom/solve");
    }

    @Test
    void analysisIsForwardedUntouchedWithAnEmptyParameterMap() {
        Map<String, Object> analysis = dispatcher.analyze(request(TypeSolver.HGS));

        assertThat(analysis).containsKey("analysis");
        // El motor de Python rechaza "parameters": null.
        assertThat(bodies.get("/custom/analyze")).contains("\"parameters\":{}");
    }

    @Test
    void aSolverWithoutAnalysisIsRejected() {
        assertThatThrownBy(() -> dispatcher.analyze(request(TypeSolver.GREEDY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not offer a previous analysis");
    }

    private void answer(String path, String json) {
        server.createContext(path, exchange -> {
            bodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private static RoutingRequest request(TypeSolver type) {
        return new RoutingRequest("P",
                List.of(new DepotDto("D1", 0.0, 0.0, 0, null)),
                List.of(new CustomerDto("C1", 20, 1.0, 1.0, 1, null)),
                List.of(new VehicleDto("V1", 100, "D1")),
                new double[][] {{0, 16}, {16, 0}},
                type,
                null);
    }
}
