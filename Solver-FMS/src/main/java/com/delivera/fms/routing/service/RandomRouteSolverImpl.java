package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service("randomRouteSolver")
public class RandomRouteSolverImpl extends BaseRouteSolver {

    @Override
    protected TypeSolver getType() {
        return TypeSolver.RANDOM;
    }

    @Override
    protected List<RouteDto> performRouting(RoutingRequest request) {
        List<CustomerDto> customers = new ArrayList<>(request.customers());
        Collections.shuffle(customers);

        List<DepotDto> depots = request.depots();
        double[][] dist = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> grouped = groupByNearestDepot(customers, depots, dist);

        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();
        return hasVehicles
                ? routesFromVehicles(vehicles, grouped, dist)
                : routesFromDepots(depots, grouped, dist);
    }

    private List<RouteDto> routesFromVehicles(List<VehicleDto> vehicles,
                                               Map<DepotDto, List<CustomerDto>> grouped,
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

        while (!remaining.isEmpty()) {
            List<String> stops = new ArrayList<>();
            double totalDistance = 0.0;
            int totalLoad = 0;
            int currentIndex = depot.matrixIndex();

            List<CustomerDto> tripCustomers = new ArrayList<>();
            for (CustomerDto c : remaining) {
                if (totalLoad + c.demand() > maxCapacity) break;
                totalDistance += dist[currentIndex][c.matrixIndex()];
                stops.add(c.id());
                totalLoad += c.demand();
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
}
