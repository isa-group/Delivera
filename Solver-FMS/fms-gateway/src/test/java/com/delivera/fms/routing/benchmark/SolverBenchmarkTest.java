package com.delivera.fms.routing.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Comprueba que todos los solvers registrados devuelven soluciones validas.
 *
 * Los solvers no estan escritos aqui: se leen de {@code GET /api/v1/fms/solvers}, que se deriva de
 * la configuracion de motores. Registrar uno nuevo basta para que entre en el benchmark, aunque
 * este implementado en otra tecnologia, porque lo unico que se le exige es el contrato HTTP.
 *
 * Es un test de integracion: necesita el sistema levantado y no corre en un build normal.
 *
 * <pre>
 * docker compose up -d
 * mvn test -Dtest=SolverBenchmarkTest -Dbenchmark=true -Dinstances=p01,p22 -Druns=3
 * </pre>
 *
 * El coste no hace fallar el test: comparar calidad es cosa de compare_solvers.py. Aqui se
 * comprueba que lo que devuelve cada motor sea una solucion valida del problema.
 */
class SolverBenchmarkTest {

    private static final Path BASE_DIR = Path.of("..");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROW = "  %-9s%3d%11.2f%11.2f%8s%7d%9s  %-4s%12s%n";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String gateway = System.getProperty("gateway", "http://localhost:8090");
    private final Duration timeout = Duration.ofSeconds(Long.getLong("timeout", 600));

    @Test
    @EnabledIfSystemProperty(named = "benchmark", matches = "true")
    void everySolverProducesFeasibleSolutions() throws Exception {
        List<Solver> solvers = discoverSolvers();
        Map<String, Double> bestKnown = MAPPER.readValue(
                Files.readString(BASE_DIR.resolve("best-known.json")), new TypeReference<>() {});
        int runs = Integer.getInteger("runs", 1);
        List<String> failures = new ArrayList<>();
        for (String name : instances()) {
            CordeauInstance instance = CordeauInstance.load(BASE_DIR.resolve("instances-MD-CVRP-JSON"), name);
            Double bks = bestKnown.get(name);
            for (Solver solver : solvers) {
                // Repetir un solver determinista solo gasta tiempo.
                int attempts = solver.deterministic() ? 1 : runs;
                List<Run> results = new ArrayList<>();

                for (int attempt = 1; attempt <= attempts; attempt++) {
                    long started = System.nanoTime();
                    JsonNode response = solve(name, solver.type());
                    long millis = (System.nanoTime() - started) / 1_000_000;

                    List<String> violations = instance.violations(response);
                    violations.forEach(violation -> failures.add(
                            "%s / %s: %s".formatted(name, solver.type(), violation)));
                    results.add(new Run(instance.cost(response),
                            response.get("routes").size(), millis, violations.isEmpty(),
                            seed(response)));
                }

                printRow(solver, results, bks);
            }
        }

        if (!failures.isEmpty()) {
            fail("%d violacion(es) de restricciones:%n  - %s"
                    .formatted(failures.size(), String.join("\n  - ", failures)));
        }
    }

    private void printRow(Solver solver, List<Run> results, Double bks) {
        Run best = results.stream().min(Comparator.comparingDouble(Run::cost)).orElseThrow();
        double mean = results.stream().mapToDouble(Run::cost).average().orElseThrow();
        long millis = (long) results.stream().mapToLong(Run::millis).average().orElseThrow();

        System.out.printf(ROW, solver.type(), results.size(), best.cost(), mean,
                bks == null ? "-" : "%+.1f%%".formatted((best.cost() / bks - 1) * 100),
                best.routes(),
                millis >= 1000 ? "%.1fs".formatted(millis / 1000.0) : millis + "ms",
                results.stream().allMatch(Run::feasible) ? "si" : "NO",
                // Semilla de la mejor de las repeticiones: es la que hay que reenviar
                // en 'parameters' para volver a obtener exactamente esta solucion.
                best.seed() == null ? "-" : best.seed());
    }

    /** Semilla efectiva que informa el motor. Ausente en los solvers deterministas. */
    private static Long seed(JsonNode response) {
        JsonNode seed = response.get("seed");
        return (seed == null || seed.isNull()) ? null : seed.asLong();
    }

    /**
     * Lee el catalogo del gateway. Es la fuente de verdad de que solvers hay: refleja los que estan
     * habilitados en configuracion, no los que existan como constante del enum.
     */
    private List<Solver> discoverSolvers() throws Exception {
        JsonNode catalog;
        try {
            catalog = send(HttpRequest.newBuilder(URI.create(gateway + "/api/v1/fms/solvers")).GET());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer el catalogo en " + gateway
                    + ". El sistema tiene que estar levantado: docker compose up -d", ex);
        }

        List<String> wanted = split(System.getProperty("solvers", "")).stream()
                .map(String::toUpperCase).toList();

        List<Solver> solvers = new ArrayList<>();
        for (JsonNode solver : catalog.get("solvers")) {
            String type = solver.get("type").asText();
            if (wanted.isEmpty() || wanted.contains(type)) {
                solvers.add(new Solver(type, solver.get("deterministic").asBoolean()));
            }
        }
        if (solvers.isEmpty()) {
            throw new IllegalStateException("Ningun solver del catalogo coincide con -Dsolvers");
        }
        return solvers;
    }

    private List<String> instances() throws Exception {
        String requested = System.getProperty("instances", "p01");
        if (!requested.equalsIgnoreCase("all")) {
            return split(requested);
        }
        try (var files = Files.list(BASE_DIR.resolve("instances-MD-CVRP-JSON"))) {
            return files.map(file -> file.getFileName().toString())
                    .filter(file -> file.endsWith(".json"))
                    .map(file -> file.substring(0, file.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    private JsonNode solve(String instance, String solver) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(gateway + "/api/v1/fms/instances/send"
                        + "?fileName=" + instance + "&solverType=" + solver))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    }

    private JsonNode send(HttpRequest.Builder builder) throws Exception {
        HttpResponse<String> response = http.send(builder.timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("El gateway respondio " + response.statusCode()
                    + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    private record Solver(String type, boolean deterministic) {
    }

    private record Run(double cost, int routes, long millis, boolean feasible, Long seed) {
    }
}
