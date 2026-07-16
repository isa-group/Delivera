package com.delivera.fms.engine.genetic.scheduler;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.VehicleDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteScheduler {

    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;
    private final Map<String, List<VehicleDto>> vehiclesByDepot;
    private final boolean hasVehicles;
    private final Map<String, Integer> customerIndexById;

    public RouteScheduler(List<CustomerDto> customers,
                          List<DepotDto> depots,
                          double[][] distanceMatrix,
                          List<VehicleDto> vehicles) {
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
        this.hasVehicles = vehicles != null && !vehicles.isEmpty();
        this.vehiclesByDepot = new HashMap<>();
        if (hasVehicles) {
            for (VehicleDto v : vehicles) {
                vehiclesByDepot.computeIfAbsent(v.startDepotId(), k -> new ArrayList<>()).add(v);
            }
        }
        this.customerIndexById = new HashMap<>(customers.size());
        for (int i = 0; i < customers.size(); i++) {
            customerIndexById.put(customers.get(i).id(), i);
        }
    }

    public List<RouteDto> buildRoutes(DepotDto depot, List<Integer> customerOrder) {
        List<VehicleDto> depotVehicles = vehiclesByDepot.getOrDefault(depot.id(), List.of());
        List<RouteDto> routes = phase1Construct(depot, customerOrder, depotVehicles);
        routes = phase2Improve(depot, routes);
        return routes;
    }

    private List<RouteDto> phase1Construct(DepotDto depot, List<Integer> customerOrder, List<VehicleDto> vehicles) {
        List<RouteDto> routes = new ArrayList<>();
        int vehicleIdx = 0;
        int pos = 0;

        while (pos < customerOrder.size()) {
            int capacity;
            String vehicleId;
            if (hasVehicles && vehicleIdx < vehicles.size()) {
                capacity = vehicles.get(vehicleIdx).capacity();
                vehicleId = vehicles.get(vehicleIdx).id();
            } else if (hasVehicles) {
                capacity = vehicles.isEmpty() ? Integer.MAX_VALUE : vehicles.get(0).capacity();
                vehicleId = "V-GA-" + depot.id() + "-" + vehicleIdx;
            } else {
                capacity = Integer.MAX_VALUE;
                vehicleId = "V-GA-" + depot.id() + "-" + vehicleIdx;
            }

            List<String> stops = new ArrayList<>();
            double totalDistance = 0.0;
            int totalLoad = 0;
            int currentIndex = depot.matrixIndex();

            while (pos < customerOrder.size()) {
                int cIdx = customerOrder.get(pos);
                CustomerDto customer = customers.get(cIdx);
                if (totalLoad + customer.demand() > capacity && !stops.isEmpty()) break;

                totalDistance += distanceMatrix[currentIndex][customer.matrixIndex()];
                stops.add(customer.id());
                totalLoad += customer.demand();
                currentIndex = customer.matrixIndex();
                pos++;
            }

            if (!stops.isEmpty()) {
                totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];
                routes.add(new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad));
            }

            vehicleIdx++;
        }

        return routes;
    }

    private List<RouteDto> phase2Improve(DepotDto depot, List<RouteDto> routes) {
        if (routes.size() < 2) return routes;

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < routes.size() - 1; i++) {
                RouteDto current = routes.get(i);
                RouteDto next = routes.get(i + 1);

                if (current.stops().isEmpty()) continue;

                String lastCustomerId = current.stops().get(current.stops().size() - 1);
                int lastCustomerIdx = findCustomerIndex(lastCustomerId);
                CustomerDto lastCustomer = customers.get(lastCustomerIdx);

                double currentCost = current.totalDistance() + next.totalDistance();

                List<String> newCurrentStops = new ArrayList<>(current.stops());
                newCurrentStops.remove(newCurrentStops.size() - 1);

                List<String> newNextStops = new ArrayList<>();
                newNextStops.add(lastCustomerId);
                newNextStops.addAll(next.stops());

                int newCurrentLoad = current.totalLoad() - lastCustomer.demand();
                int newNextLoad = next.totalLoad() + lastCustomer.demand();

                double newCurrentDist = calculateRouteDistance(depot, newCurrentStops);
                double newNextDist = calculateRouteDistance(depot, newNextStops);
                double newCost = newCurrentDist + newNextDist;

                if (newCost < currentCost) {
                    RouteDto newCurrent = new RouteDto(current.vehicleId(), current.depotId(),
                            newCurrentStops, newCurrentDist, newCurrentLoad);
                    RouteDto newNext = new RouteDto(next.vehicleId(), next.depotId(),
                            newNextStops, newNextDist, newNextLoad);
                    routes.set(i, newCurrent);
                    routes.set(i + 1, newNext);
                    improved = true;
                }
            }
        }

        return routes;
    }

    private double calculateRouteDistance(DepotDto depot, List<String> stopIds) {
        if (stopIds.isEmpty()) return 0.0;

        double distance = 0.0;
        int currentIndex = depot.matrixIndex();

        for (String stopId : stopIds) {
            int cIdx = findCustomerIndex(stopId);
            CustomerDto customer = customers.get(cIdx);
            distance += distanceMatrix[currentIndex][customer.matrixIndex()];
            currentIndex = customer.matrixIndex();
        }

        distance += distanceMatrix[currentIndex][depot.matrixIndex()];
        return distance;
    }

    private int findCustomerIndex(String customerId) {
        return customerIndexById.getOrDefault(customerId, -1);
    }
}
