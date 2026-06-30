package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("randomRouteSolver")
public class RandomRouteSolverImpl implements RouteSolver {

    @Override
    public RoutingResponse solve(RoutingRequest request) {
        long startTime = System.currentTimeMillis();

        List<CustomerDto> customers = new ArrayList<>(request.customers());
        Collections.shuffle(customers);

        List<DepotDto> depots = request.depots();
        double[][] dist = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> grouped = groupByNearestDepot(customers, depots, dist);

        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();
        List<RouteDto> routes = hasVehicles
                ? routesFromVehicles(vehicles, grouped, dist)
                : routesFromDepots(depots, grouped, dist);

        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        return new RoutingResponse(
                request.problemId(), "COMPLETED", TypeSolver.RANDOM,
                totalCost, computationTime, routes
        );
    }

    private Map<DepotDto, List<CustomerDto>> groupByNearestDepot(
            List<CustomerDto> customers, List<DepotDto> depots, double[][] dist) {
        Map<DepotDto, List<CustomerDto>> result = new HashMap<>();
        for (DepotDto d : depots) result.put(d, new ArrayList<>());
        for (CustomerDto c : customers) {
            DepotDto nearest = depots.stream()
                    .min(Comparator.comparingDouble(d -> dist[d.matrixIndex()][c.matrixIndex()]))
                    .orElse(depots.get(0));
            result.get(nearest).add(c);
        }
        return result;
    }

    private List<RouteDto> routesFromVehicles(List<VehicleDto> vehicles,
                                               Map<DepotDto, List<CustomerDto>> grouped,
                                               double[][] dist) {
        Map<String, DepotDto> depotById = new HashMap<>();
        for (DepotDto d : grouped.keySet()) depotById.put(d.id(), d);

        List<RouteDto> routes = new ArrayList<>();
        for (VehicleDto v : vehicles) {
            DepotDto depot = depotById.get(v.startDepotId());
            if (depot == null) continue;
            routes.add(buildRoute(v.id(), depot, grouped.get(depot), dist, v.capacity()));
        }
        return routes;
    }

    private List<RouteDto> routesFromDepots(List<DepotDto> depots,
                                             Map<DepotDto, List<CustomerDto>> grouped,
                                             double[][] dist) {
        List<RouteDto> routes = new ArrayList<>();
        for (DepotDto d : depots) {
            routes.add(buildRoute("V-" + d.id(), d, grouped.get(d), dist, Integer.MAX_VALUE));
        }
        return routes;
    }

    private RouteDto buildRoute(String vehicleId, DepotDto depot,
                                 List<CustomerDto> customers, double[][] dist,
                                 int maxCapacity) {
        List<String> stops = new ArrayList<>();
        double totalDistance = 0.0;
        int totalLoad = 0;
        int currentIndex = depot.matrixIndex();

        for (CustomerDto c : customers) {
            if (totalLoad + c.demand() > maxCapacity) break;
            totalDistance += dist[currentIndex][c.matrixIndex()];
            stops.add(c.id());
            totalLoad += c.demand();
            currentIndex = c.matrixIndex();
        }
        totalDistance += dist[currentIndex][depot.matrixIndex()];

        return new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad);
    }

    @Override
    public String getSolverId() {
        return "random";
    }
}
