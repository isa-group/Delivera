package com.delivera.fms.routing.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Una instancia del banco Cordeau, con lo necesario para juzgar si una solucion es valida.
 *
 * La validacion es agnostica del solver: solo mira lo que pide la instancia, no como lo ha
 * resuelto cada motor. Eso permite exigir lo mismo a todos sin conocerlos.
 */
final class CordeauInstance {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double TOLERANCE = 1e-6;

    private final Map<String, Node> depots = new LinkedHashMap<>();
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final int capacity;
    private final int fleet;
    private final double maxDuration;

    private CordeauInstance(JsonNode root) {
        this.fleet = root.get("vehicles_per_depot").asInt();
        this.capacity = root.get("depots").get(0).get("vehicle_capacity").asInt();
        this.maxDuration = root.get("depots").get(0).get("max_duration").asDouble();

        JsonNode depotsNode = root.get("depots");
        for (int i = 0; i < depotsNode.size(); i++) {
            JsonNode depot = depotsNode.get(i);
            // El gateway numera los depositos por posicion, empezando en 1.
            depots.put(String.valueOf(i + 1), node(depot));
        }
        for (JsonNode customer : root.get("customers")) {
            customers.put(customer.get("id").asText(), new Customer(node(customer),
                    customer.get("demand").asInt(), customer.get("service_duration").asDouble()));
        }
    }

    static CordeauInstance load(Path directory, String name) throws IOException {
        return new CordeauInstance(MAPPER.readTree(Files.readString(directory.resolve(name + ".json"))));
    }

    String describe() {
        String limit = maxDuration > 0 ? String.valueOf((int) maxDuration) : "sin limite";
        return "%d depositos, %d clientes, duracion maxima %s"
                .formatted(depots.size(), customers.size(), limit);
    }

    /**
     * Coste real de la solucion, recalculado desde las paradas.
     *
     * Un motor que se equivoque al sumar, o que informe un coste que no corresponde a las rutas
     * que ha entregado, no puede quedar impune por haberlo calculado el mismo.
     */
    double cost(JsonNode response) {
        double total = 0.0;
        for (JsonNode route : routes(response)) {
            total += distanceOf(route);
        }
        return total;
    }

    /**
     * Todas las restricciones que incumple la solucion, no solo la primera: asi se ve de un vistazo
     * si a un motor se le escapa una restriccion concreta o si la solucion esta rota entera.
     */
    List<String> violations(JsonNode response) {
        List<String> problems = new ArrayList<>();
        Set<String> served = new HashSet<>();
        Map<String, Set<String>> vehiclesByDepot = new HashMap<>();

        for (JsonNode route : routes(response)) {
            String vehicle = route.get("vehicleId").asText();
            String depotId = route.get("depotId").asText();
            if (!depots.containsKey(depotId)) {
                problems.add("Ruta %s sale de un deposito inexistente: %s".formatted(vehicle, depotId));
                continue;
            }
            vehiclesByDepot.computeIfAbsent(depotId, key -> new HashSet<>()).add(vehicle);

            int load = 0;
            double service = 0.0;
            for (JsonNode stop : route.get("stops")) {
                Customer customer = customers.get(stop.asText());
                if (customer == null) {
                    problems.add("Ruta %s visita un cliente inexistente: %s".formatted(vehicle, stop.asText()));
                    continue;
                }
                if (!served.add(stop.asText())) {
                    problems.add("Cliente %s servido mas de una vez".formatted(stop.asText()));
                }
                load += customer.demand();
                service += customer.service();
            }

            if (load > capacity) {
                problems.add("Ruta %s excede capacidad: %d > %d".formatted(vehicle, load, capacity));
            }
            if (route.get("totalLoad").asInt() != load) {
                problems.add("Ruta %s informa carga %d pero sus paradas suman %d"
                        .formatted(vehicle, route.get("totalLoad").asInt(), load));
            }
            // La duracion de una ruta es su distancia mas los tiempos de servicio de sus paradas.
            double duration = distanceOf(route) + service;
            if (maxDuration > 0 && duration > maxDuration + TOLERANCE) {
                problems.add("Ruta %s excede duracion: %.2f > %.2f".formatted(vehicle, duration, maxDuration));
            }
        }

        customers.keySet().stream()
                .filter(id -> !served.contains(id))
                .forEach(id -> problems.add("Cliente %s sin servir".formatted(id)));

        // Se cuentan vehiculos distintos, no rutas: hay motores que modelan multi-viaje y reutilizan
        // el mismo vehiculo en varias rutas. Contar rutas les exigiria algo que el problema no pide.
        vehiclesByDepot.forEach((depotId, vehicles) -> {
            if (vehicles.size() > fleet) {
                problems.add("Deposito %s usa %d vehiculos, disponibles %d"
                        .formatted(depotId, vehicles.size(), fleet));
            }
        });

        return problems;
    }

    // Distancia del ciclo deposito -> paradas -> deposito
    private double distanceOf(JsonNode route) {
        Node depot = depots.get(route.get("depotId").asText());
        if (depot == null) {
            return 0.0;
        }
        Node current = depot;
        double distance = 0.0;
        for (JsonNode stop : route.get("stops")) {
            Customer customer = customers.get(stop.asText());
            if (customer != null) {
                distance += current.distanceTo(customer.node());
                current = customer.node();
            }
        }
        return distance + current.distanceTo(depot);
    }

    private static Iterable<JsonNode> routes(JsonNode response) {
        JsonNode routes = response.get("routes");
        return (routes == null || routes.isNull()) ? List.of() : routes;
    }

    private static Node node(JsonNode entry) {
        return new Node(entry.get("x").asDouble(), entry.get("y").asDouble());
    }

    private record Node(double x, double y) {
        double distanceTo(Node other) {
            return Math.hypot(x - other.x, y - other.y);
        }
    }

    private record Customer(Node node, int demand, double service) {
    }
}
