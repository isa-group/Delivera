package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("greedyRouteSolver")
public class GreedyRouteSolverImpl extends BaseRouteSolver {

    private static final Logger log = LoggerFactory.getLogger(GreedyRouteSolverImpl.class);

    @Override
    protected TypeSolver getType() {
        return TypeSolver.GREEDY;
    }

    @Override
    protected List<RouteDto> performRouting(RoutingRequest request) {
        log.debug("Starting GREEDY solver for problem: {}", request.problemId());
        List<DepotDto> depots = request.depots();
        List<CustomerDto> customers = request.customers();
        double[][] dist = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> groupedByDepot = groupByNearestDepot(customers, depots, dist);
        Map<String, DepotDto> depotById = depots.stream()
                .collect(Collectors.toMap(DepotDto::id, Function.identity()));

        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();

        boolean[] visited = new boolean[dist.length];
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
                                                       boolean[] visited) {
        List<RouteDto> routes = new ArrayList<>();
        if (depot == null || customers == null) return routes;

        List<CustomerDto> unvisited = new ArrayList<>(customers);
        unvisited.removeIf(c -> visited[c.matrixIndex()]);

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
                visited[c.matrixIndex()] = true;
            }
            unvisited = currentUnvisited;
            totalStopsAssigned += stops.size();
        }

        return routes;
    }
}
