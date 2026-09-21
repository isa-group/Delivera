package com.delivera.fms.engine.annealing.benchmark;

import com.delivera.fms.engine.annealing.dto.RoutingRequest;
import com.delivera.fms.engine.annealing.dto.RoutingResponse;
import com.delivera.fms.engine.annealing.dto.TracePointDto;
import com.delivera.fms.engine.annealing.service.AnnealingRouteSolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banco de calibracion: ejecuta el motor sobre instancias Cordeau e imprime coste, gap frente al
 * BKS, factibilidad y curva anytime. No afirma nada, es para mirar; por eso solo corre a peticion.
 *
 * <pre>
 * mvn test -Dtest=AnnealingCalibrationTest -Dcalibration=true -Dinstances=p01,p22 -DtimeLimitMs=5000 -Dseed=1
 * </pre>
 */
@EnabledIfSystemProperty(named = "calibration", matches = "true")
class AnnealingCalibrationTest {

    @Test
    void printResults() throws Exception {
        String[] instances = System.getProperty("instances", "p01,p03,p07,p11,p15,p22,pr06").split(",");
        long seed = Long.parseLong(System.getProperty("seed", "20260914"));
        Map<String, Double> bks = bestKnown();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("seed", seed);
        for (String name : List.of("timeLimitMs", "maxLevels", "calibrationQuantile", "initialAcceptanceRate", "finalAcceptanceRate",
                "coolingRate", "movesPerTemperatureFactor", "interDepotMoveProbability",
                "depotCandidateRatio", "localSearchFrequency", "rebalanceFrequency")) {
            String value = System.getProperty(name);
            if (value != null) {
                parameters.put(name, Double.parseDouble(value));
            }
        }

        System.out.printf("%-6s %10s %10s %8s %9s %8s  %s%n",
                "inst", "coste", "BKS", "gap", "tiempo", "factible", "traza (ms:coste)");
        for (String instance : instances) {
            RoutingRequest request = CordeauInstanceLoader.load(instance.trim(), parameters);
            RoutingResponse response = new AnnealingRouteSolver().solve(request);

            double cost = FeasibilityChecker.cost(request, response);
            List<String> violations = FeasibilityChecker.violations(request, response);
            double reference = bks.getOrDefault(instance.trim(), Double.NaN);
            StringBuilder trace = new StringBuilder();
            for (TracePointDto point : response.trace()) {
                trace.append(point.elapsedMs()).append(':').append(String.format("%.0f", point.cost())).append(' ');
            }
            System.out.printf("%-6s %10.2f %10.2f %7.1f%% %7dms %8s  %s%n",
                    instance, cost, reference, (cost / reference - 1) * 100, response.computationTimeMs(),
                    violations.isEmpty() ? "si" : "NO", trace);
            if (!violations.isEmpty()) {
                System.out.println("       " + violations);
            }
        }
    }

    private static Map<String, Double> bestKnown() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(Path.of("..", "..", "best-known.json")));
        Map<String, Double> bks = new HashMap<>();
        root.fields().forEachRemaining(entry -> bks.put(entry.getKey(), entry.getValue().asDouble()));
        return bks;
    }
}
