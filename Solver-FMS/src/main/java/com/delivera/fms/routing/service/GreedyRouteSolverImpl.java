package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("greedyRouteSolver")
public class GreedyRouteSolverImpl extends BaseRouteSolver {

    @Override
    protected TypeSolver getType() {
        return TypeSolver.GREEDY;
    }

    @Override
    protected List<RouteDto> performRouting(RoutingRequest request) {
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
                    addRoute(routes, buildGreedyRoute(v.id(), depot, depotCustomers,
                            dist, maxStops, visited));
                }
            }
        } else {
            for (DepotDto d : depots) {
                addRoute(routes, buildGreedyRoute("V-" + d.id(), d, groupedByDepot.get(d),
                        dist, Integer.MAX_VALUE, visited));
            }
        }

        return routes;
    }

    private RouteDto buildGreedyRoute(String vehicleId, DepotDto depot,
                                       List<CustomerDto> customers, double[][] dist,
                                       int maxStops, Set<String> visited) {
        if (depot == null || customers == null) return null;

        List<String> stops = new ArrayList<>();
        double totalDistance = 0.0;
        int totalLoad = 0;
        int currentIndex = depot.matrixIndex();

        List<CustomerDto> unvisited = new ArrayList<>(customers);
        unvisited.removeIf(c -> visited.contains(c.id()));

        while (!unvisited.isEmpty() && stops.size() < maxStops) {
            CustomerDto best = null;
            double bestDist = Double.MAX_VALUE;

            for (CustomerDto c : unvisited) {
                double d = dist[currentIndex][c.matrixIndex()];
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }

            if (best == null) break;

            totalDistance += bestDist;
            stops.add(best.id());
            totalLoad += best.demand();
            currentIndex = best.matrixIndex();
            visited.add(best.id());
            unvisited.remove(best);
        }

        totalDistance += dist[currentIndex][depot.matrixIndex()];
        return new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad);
    }
}
