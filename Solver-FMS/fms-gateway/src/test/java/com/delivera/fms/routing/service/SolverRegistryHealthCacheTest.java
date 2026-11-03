package com.delivera.fms.routing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.fms.routing.config.EngineProperties;
import com.delivera.fms.routing.config.HealthCacheProperties;
import com.delivera.fms.routing.dto.SolverInfo;
import com.delivera.fms.routing.dto.SolverStatus;
import com.delivera.fms.routing.dto.TypeSolver;
import com.sun.net.httpserver.HttpServer;

class SolverRegistryHealthCacheTest {

    private final AtomicInteger healthCalls = new AtomicInteger();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    private HttpServer server;
    private volatile int responseStatus;
    private volatile long responseDelayMs;
    private SolverRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/actuator/health", exchange -> {
            healthCalls.incrementAndGet();
            sleep(responseDelayMs);
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        EngineProperties.Engine engine = new EngineProperties.Engine();
        engine.setUrl("http://localhost:" + server.getAddress().getPort());

        // QISKIT y DWAVE comparten motor, como en la configuracion real.
        EngineProperties.Engine quantum = new EngineProperties.Engine();
        quantum.setUrl(engine.getUrl().replace("localhost", "127.0.0.1"));

        EngineProperties properties = new EngineProperties();
        properties.setEngines(Map.of(TypeSolver.GREEDY, engine, TypeSolver.QISKIT, quantum, TypeSolver.DWAVE, quantum));

        registry = new SolverRegistry(properties, WebClient.builder(),
                new HealthStatusCache(new HealthCacheProperties(), clock));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void anUpEngineIsNotProbedAgainUntilThePositiveTtlExpires() {
        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(2);

        clock.advance(Duration.ofSeconds(31));

        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(4);
    }

    @Test
    void aDownEngineIsRetriedSoonSoItsRecoveryIsSeen() {
        responseStatus = 500;

        assertThat(status()).isEqualTo(SolverStatus.DOWN);
        assertThat(healthCalls).hasValue(2);

        clock.advance(Duration.ofSeconds(6));
        responseStatus = 200;

        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(4);
    }

    @Test
    void solversSharingAnEngineAreProbedOnce() {
        assertThat(statusOf(TypeSolver.QISKIT)).isEqualTo(SolverStatus.UP);
        assertThat(statusOf(TypeSolver.DWAVE)).isEqualTo(SolverStatus.UP);

        // Un catalogo con GREEDY, QISKIT y DWAVE: dos motores, dos sondas.
        assertThat(healthCalls).hasValue(2);
    }

    @Test
    void simultaneousRequestsWaitForTheSameProbe() {
        responseDelayMs = 300;

        CompletableFuture<SolverStatus> first = CompletableFuture.supplyAsync(this::status);
        CompletableFuture<SolverStatus> second = CompletableFuture.supplyAsync(this::status);

        assertThat(first.join()).isEqualTo(SolverStatus.UP);
        assertThat(second.join()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(2);
    }

    @Test
    void aSuccessfulSolveCountsAsAHealthCheck() {
        registry.markHealthy(TypeSolver.GREEDY);
        registry.markHealthy(TypeSolver.QISKIT);

        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(0);
    }

    @Test
    void aFailedSolveForcesANewProbe() {
        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(2);

        registry.forgetHealth(TypeSolver.GREEDY);

        assertThat(status()).isEqualTo(SolverStatus.UP);
        assertThat(healthCalls).hasValue(3);
    }

    private SolverStatus status() {
        return statusOf(TypeSolver.GREEDY);
    }

    private SolverStatus statusOf(TypeSolver type) {
        return registry.findAll(true).stream()
                .filter(info -> info.type() == type)
                .map(SolverInfo::status)
                .findFirst()
                .orElseThrow();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
