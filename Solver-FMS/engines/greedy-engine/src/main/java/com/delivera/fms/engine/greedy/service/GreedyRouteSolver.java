package com.delivera.fms.engine.greedy.service;

import com.delivera.fms.engine.greedy.dto.CustomerDto;
import com.delivera.fms.engine.greedy.dto.DepotDto;
import com.delivera.fms.engine.greedy.dto.RouteDto;
import com.delivera.fms.engine.greedy.dto.RoutingRequest;
import com.delivera.fms.engine.greedy.dto.RoutingResponse;
import com.delivera.fms.engine.greedy.dto.VehicleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GreedyRouteSolver {

    private static final Logger log = LoggerFactory.getLogger(GreedyRouteSolver.class);
    private static final String SOLVER_TYPE = "GREEDY";

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
        List<DepotDto> depots = request.depots();
        List<CustomerDto> customers = request.customers();
        double[][] dist = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> groupedByDepot = groupByNearestDepot(customers, depots, dist);
        Map<String, DepotDto> depotById = depots.stream()
                .collect(Collectors.toMap(DepotDto::id, Function.identity()));

        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();

        Set<String> visited = new HashSet<>();
        List<RouteDto> routes = new ArrayList<>();

        if (hasVehicles) {
            Map<String, List<VehicleDto>> vehiclesByDepot = vehicles.stream()
                    .collect(Collectors.groupingBy(VehicleDto::startDepotId));

            for (Map.Entry<String, List<VehicleDto>> entry : vehiclesByDepot.entrySet()) {
                String depotId = entry.getKey();
                List<VehicleDto> depotVehicles = entry.getValue();
                DepotDto depot = depotById.get(depotId);
                List<CustomerDto> depotCustomers = groupedByDepot.get(depot);

                if (depot == null || depotCustomers == null || depotCustomers.isEmpty()) continue;

                int totalCustomers = depotCustomers.size();
                int vehicleCount = depotVehicles.size();
                int baseStops = totalCustomers / vehicleCount;

                for (int i = 0; i < depotVehicles.size(); i++) {
                    VehicleDto v = depotVehicles.get(i);
                    int maxStops = i == depotVehicles.size() - 1
                            ? Integer.MAX_VALUE
                            : baseStops;
                    routes.addAll(buildGreedyMultiTripRoutes(v.id(), depot, depotCustomers,
                            dist, maxStops, v.capacity(), visited));
                }
            }

            List<CustomerDto> unvisitedCustomers = customers.stream()
                    .filter(c -> !visited.contains(c.id()))
                    .toList();
            // Si el depot no tiene vehiculos asignados, se asigna un depot fallback para los clientes no visitados
            if (!unvisitedCustomers.isEmpty()) {
                DepotDto fallbackDepot = depots.stream()
                        .filter(d -> vehiclesByDepot.containsKey(d.id()))
                        .findFirst()
                        .orElse(depots.get(0));

                String fallbackVehicleId = "V-FALLBACK-" + fallbackDepot.id();
                routes.addAll(buildGreedyMultiTripRoutes(fallbackVehicleId, fallbackDepot, unvisitedCustomers,
                        dist, Integer.MAX_VALUE, Integer.MAX_VALUE, visited));
            }
        } else {
            for (DepotDto d : depots) {
                routes.addAll(buildGreedyMultiTripRoutes("V-" + d.id(), d, groupedByDepot.get(d),
                        dist, Integer.MAX_VALUE, Integer.MAX_VALUE, visited));
            }
        }

        return routes;
    }

    private List<RouteDto> buildGreedyMultiTripRoutes(String vehicleId, DepotDto depot,
                                                       List<CustomerDto> customers, double[][] dist,
                                                       int maxStops, int maxCapacity,
                                                       Set<String> visited) {
        List<RouteDto> routes = new ArrayList<>();
        if (depot == null || customers == null) return routes;

        List<CustomerDto> unvisited = new ArrayList<>(customers);
        unvisited.removeIf(c -> visited.contains(c.id()));

        int totalStopsAssigned = 0;

        while (!unvisited.isEmpty() && totalStopsAssigned < maxStops) {
            List<String> stops = new ArrayList<>();
            double totalDistance = 0.0;
            int totalLoad = 0;
            int currentIndex = depot.matrixIndex();

            List<CustomerDto> tripCustomers = new ArrayList<>();
            List<CustomerDto> currentUnvisited = new ArrayList<>(unvisited);

            while (!currentUnvisited.isEmpty() && totalStopsAssigned + stops.size() < maxStops) {
                CustomerDto best = null;
                double bestDist = Double.MAX_VALUE;

                for (CustomerDto c : currentUnvisited) {
                    double d = dist[currentIndex][c.matrixIndex()];
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }

                if (best == null) break;

                if (totalLoad + best.demand() > maxCapacity) {
                    currentUnvisited.remove(best);
                    continue;
                }

                totalDistance += bestDist;
                stops.add(best.id());
                totalLoad += best.demand();
                currentIndex = best.matrixIndex();
                tripCustomers.add(best);
                currentUnvisited.remove(best);
            }

            if (tripCustomers.isEmpty()) break;

            totalDistance += dist[currentIndex][depot.matrixIndex()];
            routes.add(new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad));

            for (CustomerDto c : tripCustomers) {
                visited.add(c.id());
                unvisited.remove(c);
            }
            totalStopsAssigned += stops.size();
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
