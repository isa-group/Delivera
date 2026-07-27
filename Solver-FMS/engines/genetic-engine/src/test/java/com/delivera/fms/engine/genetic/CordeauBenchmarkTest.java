package com.delivera.fms.engine.genetic;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.RoutingResponse;
import com.delivera.fms.engine.genetic.dto.VehicleDto;
import com.delivera.fms.engine.genetic.service.GeneticRouteSolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark reproducible contra las instancias Cordeau MDVRP.
 *
 * Ademas del coste, valida la factibilidad de la solucion (todos los clientes
 * servidos exactamente una vez, capacidad por ruta y numero de vehiculos por deposito),
 * para que una mejora de coste no pueda venir de violar restricciones.
 */
class CordeauBenchmarkTest {

    private static final Path INSTANCES_DIR = Path.of("..", "..", "instances-MD-CVRP-JSON");

    /**
     * Mejores soluciones conocidas publicadas para el conjunto Cordeau. Solo se usan para informar
     * del gap. Un coste por debajo del BKS no es un record: es la senal de que se esta violando
     * alguna restriccion de la instancia.
     */
    private static final Map<String, Double> BEST_KNOWN = Map.ofEntries(
            Map.entry("p01", 576.87), Map.entry("p02", 473.53), Map.entry("p03", 641.19),
            Map.entry("p04", 1001.59), Map.entry("p05", 750.03), Map.entry("p06", 876.50),
            Map.entry("p07", 885.80), Map.entry("p08", 4437.68), Map.entry("p09", 3900.22),
            Map.entry("p10", 3663.02), Map.entry("p11", 3554.18), Map.entry("p12", 1318.95),
            Map.entry("p13", 1318.95), Map.entry("p14", 1360.12), Map.entry("p15", 2505.42),
            Map.entry("p16", 2572.23), Map.entry("p17", 2709.09), Map.entry("p18", 3702.85),
            Map.entry("p19", 3827.06), Map.entry("p20", 4058.07), Map.entry("p21", 5474.84),
            Map.entry("p22", 5702.16), Map.entry("p23", 6095.46),
            Map.entry("pr01", 861.32), Map.entry("pr02", 1307.34), Map.entry("pr03", 1803.80),
            Map.entry("pr04", 2058.31), Map.entry("pr05", 2331.20), Map.entry("pr06", 2676.30),
            Map.entry("pr07", 1089.56), Map.entry("pr08", 1664.85), Map.entry("pr09", 2153.10),
            Map.entry("pr10", 2921.85)
    );

    @Test
    @EnabledIfSystemProperty(named = "benchmark", matches = "true")
    void benchmarkInstance() throws Exception {
        String instance = System.getProperty("instance", "p22");
        int runs = Integer.parseInt(System.getProperty("runs", "1"));

        Instance parsed = loadInstance(instance);
        RoutingRequest request = toRoutingRequest(parsed);
        GeneticRouteSolver solver = new GeneticRouteSolver();

        double best = Double.MAX_VALUE;
        double sum = 0.0;
        long totalMs = 0;

        for (int run = 0; run < runs; run++) {
            long start = System.currentTimeMillis();
            RoutingResponse response = solver.solve(request);
            long elapsed = System.currentTimeMillis() - start;

            assertFeasible(response, parsed, request);

            double cost = response.totalCost();
            best = Math.min(best, cost);
            sum += cost;
            totalMs += elapsed;

            System.out.printf("  run %d: cost=%.2f  routes=%d  time=%dms%n",
                    run + 1, cost, response.routes().size(), elapsed);
        }

        Double bks = BEST_KNOWN.get(instance);
        System.out.printf("%n[%s] runs=%d  best=%.2f  avg=%.2f  avgTime=%dms",
                instance, runs, best, sum / runs, totalMs / runs);
        if (bks != null) {
            System.out.printf("  BKS=%.2f  gapBest=%.2f%%  gapAvg=%.2f%%",
                    bks, (best / bks - 1) * 100, (sum / runs / bks - 1) * 100);
        }
        System.out.println();
    }

    private void assertFeasible(RoutingResponse response, Instance instance, RoutingRequest request) {
        Map<String, Integer> demandById = new HashMap<>();
        Map<String, Double> serviceById = new HashMap<>();
        for (CustomerDto c : request.customers()) {
            demandById.put(c.id(), c.demand());
            serviceById.put(c.id(), c.service());
        }

        Set<String> served = new HashSet<>();
        Map<String, Integer> routesPerDepot = new HashMap<>();

        for (RouteDto route : response.routes()) {
            int load = 0;
            double service = 0.0;
            for (String stop : route.stops()) {
                assertTrue(served.add(stop), "Cliente servido mas de una vez: " + stop);
                load += demandById.getOrDefault(stop, 0);
                service += serviceById.getOrDefault(stop, 0.0);
            }
            assertEquals(load, route.totalLoad(), "totalLoad incoherente en ruta " + route.vehicleId());
            assertTrue(load <= instance.vehicleCapacity,
                    "Ruta " + route.vehicleId() + " excede capacidad: " + load + " > " + instance.vehicleCapacity);
            // La duracion de una ruta es su distancia mas los tiempos de servicio de sus paradas.
            if (instance.maxDuration > 0) {
                double duration = route.totalDistance() + service;
                assertTrue(duration <= instance.maxDuration + 1e-6,
                        String.format("Ruta %s excede duracion: %.2f > %.2f",
                                route.vehicleId(), duration, instance.maxDuration));
            }
            routesPerDepot.merge(route.depotId(), 1, Integer::sum);
        }

        assertEquals(demandById.size(), served.size(), "Hay clientes sin servir");
        routesPerDepot.forEach((depotId, count) -> assertTrue(count <= instance.vehiclesPerDepot,
                "Deposito " + depotId + " usa " + count + " vehiculos, disponibles " + instance.vehiclesPerDepot));
    }

    private Instance loadInstance(String name) throws Exception {
        Path path = INSTANCES_DIR.resolve(name + ".json");
        JsonNode root = new ObjectMapper().readTree(Files.readString(path));

        Instance instance = new Instance();
        instance.vehiclesPerDepot = root.get("vehicles_per_depot").asInt();
        instance.vehicleCapacity = root.get("depots").get(0).get("vehicle_capacity").asInt();
        instance.maxDuration = root.get("depots").get(0).get("max_duration").asDouble();

        for (JsonNode depot : root.get("depots")) {
            instance.depotX.add(depot.get("x").asDouble());
            instance.depotY.add(depot.get("y").asDouble());
            instance.depotCapacity.add(depot.get("vehicle_capacity").asInt());
        }
        for (JsonNode customer : root.get("customers")) {
            instance.customerId.add(customer.get("id").asInt());
            instance.customerX.add(customer.get("x").asDouble());
            instance.customerY.add(customer.get("y").asDouble());
            instance.customerDemand.add(customer.get("demand").asInt());
            instance.customerService.add(customer.get("service_duration").asDouble());
        }
        return instance;
    }

    /** Replica el mapeo de StandardInstanceMapper + DistanceMatrixCalculator del gateway. */
    private RoutingRequest toRoutingRequest(Instance instance) {
        List<DepotDto> depots = new ArrayList<>();
        List<VehicleDto> vehicles = new ArrayList<>();
        for (int i = 0; i < instance.depotX.size(); i++) {
            String depotId = String.valueOf(i + 1);
            depots.add(new DepotDto(depotId, instance.depotY.get(i), instance.depotX.get(i), i,
                    instance.maxDuration));
            for (int v = 0; v < instance.vehiclesPerDepot; v++) {
                vehicles.add(new VehicleDto("V" + depotId + "-" + (v + 1), instance.depotCapacity.get(i), depotId));
            }
        }

        List<CustomerDto> customers = new ArrayList<>();
        int offset = depots.size();
        for (int i = 0; i < instance.customerId.size(); i++) {
            customers.add(new CustomerDto(
                    String.valueOf(instance.customerId.get(i)),
                    instance.customerDemand.get(i),
                    instance.customerY.get(i),
                    instance.customerX.get(i),
                    offset + i,
                    instance.customerService.get(i)));
        }

        return new RoutingRequest("bench", depots, customers, vehicles, distanceMatrix(depots, customers));
    }

    private double[][] distanceMatrix(List<DepotDto> depots, List<CustomerDto> customers) {
        int n = depots.size() + customers.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (DepotDto d : depots) {
            x[d.matrixIndex()] = d.lng();
            y[d.matrixIndex()] = d.lat();
        }
        for (CustomerDto c : customers) {
            x[c.matrixIndex()] = c.lng();
            y[c.matrixIndex()] = c.lat();
        }

        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double distance = Math.hypot(x[i] - x[j], y[i] - y[j]);
                matrix[i][j] = distance;
                matrix[j][i] = distance;
            }
        }
        return matrix;
    }

    private static class Instance {
        int vehiclesPerDepot;
        int vehicleCapacity;
        double maxDuration;
        final List<Double> depotX = new ArrayList<>();
        final List<Double> depotY = new ArrayList<>();
        final List<Integer> depotCapacity = new ArrayList<>();
        final List<Integer> customerId = new ArrayList<>();
        final List<Double> customerX = new ArrayList<>();
        final List<Double> customerY = new ArrayList<>();
        final List<Integer> customerDemand = new ArrayList<>();
        final List<Double> customerService = new ArrayList<>();
    }
}
