package com.delivera.fms.engine.genetic.benchmark;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.RoutingResponse;
import com.delivera.fms.engine.genetic.dto.VehicleDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comprueba una respuesta contra las restricciones de la peticion, sin fiarse de lo que informa el
 * motor: recalcula distancia y carga desde las paradas.
 */
final class FeasibilityChecker {

    private static final double TOLERANCE = 1e-6;

    private FeasibilityChecker() {
    }

    // Coste real de la solucion, sumado desde las paradas
    static double cost(RoutingRequest request, RoutingResponse response) {
        Map<String, DepotDto> depots = byId(request.depots(), DepotDto::id);
        Map<String, CustomerDto> customers = byId(request.customers(), CustomerDto::id);
        double total = 0.0;
        for (RouteDto route : response.routes()) {
            total += distance(request.distanceMatrix(), depots.get(route.depotId()), route, customers);
        }
        return total;
    }

    // Todas las restricciones incumplidas, no solo la primera.
    static List<String> violations(RoutingRequest request, RoutingResponse response) {
        Map<String, DepotDto> depots = byId(request.depots(), DepotDto::id);
        Map<String, CustomerDto> customers = byId(request.customers(), CustomerDto::id);
        Map<String, Integer> capacityByDepot = new HashMap<>();
        Map<String, Integer> fleetByDepot = new HashMap<>();
        for (VehicleDto vehicle : request.vehicles()) {
            capacityByDepot.merge(vehicle.startDepotId(), vehicle.capacity(), Math::max);
            fleetByDepot.merge(vehicle.startDepotId(), 1, Integer::sum);
        }

        List<String> problems = new ArrayList<>();
        Set<String> served = new HashSet<>();
        Map<String, Set<String>> vehiclesByDepot = new HashMap<>();

        for (RouteDto route : response.routes()) {
            DepotDto depot = depots.get(route.depotId());
            if (depot == null) {
                problems.add("Ruta " + route.vehicleId() + " sale de un deposito inexistente: " + route.depotId());
                continue;
            }
            vehiclesByDepot.computeIfAbsent(depot.id(), key -> new HashSet<>()).add(route.vehicleId());

            int load = 0;
            double service = 0.0;
            for (String stop : route.stops()) {
                CustomerDto customer = customers.get(stop);
                if (customer == null) {
                    problems.add("Ruta " + route.vehicleId() + " visita un cliente inexistente: " + stop);
                    continue;
                }
                if (!served.add(stop)) {
                    problems.add("Cliente " + stop + " servido mas de una vez");
                }
                load += customer.demand();
                service += customer.service();
            }

            int capacity = capacityByDepot.getOrDefault(depot.id(), Integer.MAX_VALUE);
            if (load > capacity) {
                problems.add("Ruta " + route.vehicleId() + " excede capacidad: " + load + " > " + capacity);
            }
            if (route.totalLoad() != load) {
                problems.add("Ruta " + route.vehicleId() + " informa carga " + route.totalLoad()
                        + " pero sus paradas suman " + load);
            }
            double duration = distance(request.distanceMatrix(), depot, route, customers) + service;
            if (duration > depot.durationLimit() + TOLERANCE) {
                problems.add("Ruta %s excede duracion: %.2f > %.2f"
                        .formatted(route.vehicleId(), duration, depot.durationLimit()));
            }
        }

        for (CustomerDto customer : request.customers()) {
            if (!served.contains(customer.id())) {
                problems.add("Cliente " + customer.id() + " sin servir");
            }
        }
        vehiclesByDepot.forEach((depotId, vehicles) -> {
            int fleet = fleetByDepot.getOrDefault(depotId, Integer.MAX_VALUE);
            if (vehicles.size() > fleet) {
                problems.add("Deposito " + depotId + " usa " + vehicles.size() + " vehiculos, disponibles " + fleet);
            }
        });

        return problems;
    }

    private static double distance(double[][] matrix, DepotDto depot, RouteDto route,
                                   Map<String, CustomerDto> customers) {
        if (depot == null) {
            return 0.0;
        }
        int current = depot.matrixIndex();
        double distance = 0.0;
        for (String stop : route.stops()) {
            CustomerDto customer = customers.get(stop);
            if (customer != null) {
                distance += matrix[current][customer.matrixIndex()];
                current = customer.matrixIndex();
            }
        }
        return distance + matrix[current][depot.matrixIndex()];
    }

    private static <T> Map<String, T> byId(List<T> items, Function<T, String> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
