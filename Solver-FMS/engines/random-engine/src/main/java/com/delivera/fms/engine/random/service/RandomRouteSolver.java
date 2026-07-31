package com.delivera.fms.engine.random.service;

import com.delivera.fms.engine.random.dto.CustomerDto;
import com.delivera.fms.engine.random.dto.DepotDto;
import com.delivera.fms.engine.random.dto.RouteDto;
import com.delivera.fms.engine.random.dto.RoutingRequest;
import com.delivera.fms.engine.random.dto.RoutingResponse;
import com.delivera.fms.engine.random.dto.VehicleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RandomRouteSolver {

    private static final Logger log = LoggerFactory.getLogger(RandomRouteSolver.class);
    private static final String SOLVER_TYPE = "RANDOM";

    public RoutingResponse solve(RoutingRequest request) {
        log.info("Solving problem '{}' with solver: {}", request.problemId(), SOLVER_TYPE);
        long startTime = System.currentTimeMillis();

        List<RouteDto> routes = performRouting(request);
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        log.info("Problem '{}' solved. Routes: {}, Total cost: {}, Time: {}ms",
                request.problemId(), routes.size(), totalCost, computationTime);

        return new RoutingResponse(
                request.problemId(), "COMPLETED", SOLVER_TYPE,
                totalCost, computationTime, routes
        );
    }

    private List<RouteDto> performRouting(RoutingRequest request) {
        List<CustomerDto> customers = new ArrayList<>(request.customers());
        Collections.shuffle(customers);

        List<DepotDto> depots = request.depots();
        double[][] dist = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> grouped = groupByNearestDepot(customers, depots, dist);

        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();
        return hasVehicles
                ? routesFromVehicles(vehicles, customers, grouped, depots, dist)
                : routesFromDepots(depots, grouped, dist);
    }

    private List<RouteDto> routesFromVehicles(List<VehicleDto> vehicles,
                                               List<CustomerDto> allCustomers,
                                               Map<DepotDto, List<CustomerDto>> grouped,
                                               List<DepotDto> depots,
                                               double[][] dist) {
        Map<String, DepotDto> depotById = new HashMap<>();
        for (DepotDto d : grouped.keySet()) depotById.put(d.id(), d);

        Set<String> visited = new HashSet<>();
        List<RouteDto> routes = new ArrayList<>();
        for (VehicleDto v : vehicles) {
            DepotDto depot = depotById.get(v.startDepotId());
            if (depot == null) continue;
            routes.addAll(buildMultiTripRoutes(v.id(), depot, grouped.get(depot), dist, v.capacity(), visited));
        }
    
        List<CustomerDto> unvisitedCustomers = allCustomers.stream()
                .filter(c -> !visited.contains(c.id()))
                .toList();
        // Si el depot no tiene vehiculos asignados, se asigna un depot fallback para los clientes no visitados
        if (!unvisitedCustomers.isEmpty()) {
            DepotDto fallbackDepot = depots.stream()
                    .filter(d -> depotById.containsKey(d.id()))
                    .findFirst()
                    .orElse(depots.get(0));

            String fallbackVehicleId = "V-FALLBACK-" + fallbackDepot.id();
            routes.addAll(buildMultiTripRoutes(fallbackVehicleId, fallbackDepot, unvisitedCustomers,
                    dist, Integer.MAX_VALUE, visited));
        }

        return routes;
    }

    private List<RouteDto> routesFromDepots(List<DepotDto> depots,
                                             Map<DepotDto, List<CustomerDto>> grouped,
                                             double[][] dist) {
        Set<String> visited = new HashSet<>();
        List<RouteDto> routes = new ArrayList<>();
        for (DepotDto d : depots) {
            routes.addAll(buildMultiTripRoutes("V-" + d.id(), d, grouped.get(d), dist, Integer.MAX_VALUE, visited));
        }
        return routes;
    }

    private List<RouteDto> buildMultiTripRoutes(String vehicleId, DepotDto depot,
                                                 List<CustomerDto> customers, double[][] dist,
                                                 int maxCapacity, Set<String> visited) {
        List<RouteDto> routes = new ArrayList<>();
        if (customers == null || customers.isEmpty()) return routes;

        List<CustomerDto> remaining = new ArrayList<>();
        for (CustomerDto c : customers) {
            if (!visited.contains(c.id())) remaining.add(c);
        }

        double durationLimit = depot.durationLimit();

        while (!remaining.isEmpty()) {
            List<String> stops = new ArrayList<>();
            double totalDistance = 0.0;
            int totalLoad = 0;
            double totalService = 0.0;
            int currentIndex = depot.matrixIndex();

            List<CustomerDto> tripCustomers = new ArrayList<>();
            for (CustomerDto c : remaining) {
                if (totalLoad + c.demand() > maxCapacity) break;

                // La duracion se mide sobre la ruta cerrada, con la vuelta al deposito.
                // Un cliente que no cabe ni el solo se acepta igualmente: dejarlo fuera
                // seria peor que pasarse, porque quedaria sin servir.
                double closed = totalDistance + dist[currentIndex][c.matrixIndex()]
                        + dist[c.matrixIndex()][depot.matrixIndex()]
                        + totalService + c.service();
                if (closed > durationLimit && !tripCustomers.isEmpty()) {
                    break;
                }

                totalDistance += dist[currentIndex][c.matrixIndex()];
                stops.add(c.id());
                totalLoad += c.demand();
                totalService += c.service();
                currentIndex = c.matrixIndex();
                tripCustomers.add(c);
            }

            if (tripCustomers.isEmpty()) break;

            totalDistance += dist[currentIndex][depot.matrixIndex()];
            routes.add(new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad));

            for (CustomerDto c : tripCustomers) {
                visited.add(c.id());
            }
            remaining.removeAll(tripCustomers);
        }

        return routes;
    }

    private Map<DepotDto, List<CustomerDto>> groupByNearestDepot(
            List<CustomerDto> customers, List<DepotDto> depots, double[][] dist) {
        Map<DepotDto, List<CustomerDto>> result = new HashMap<>();
        for (CustomerDto c : customers) {
            DepotDto nearest = depots.stream()
                    .min(Comparator.comparingDouble(d -> dist[d.matrixIndex()][c.matrixIndex()]))
                    .orElse(depots.get(0));
            result.computeIfAbsent(nearest, k -> new ArrayList<>()).add(c);
        }
        return result;
    }
}
