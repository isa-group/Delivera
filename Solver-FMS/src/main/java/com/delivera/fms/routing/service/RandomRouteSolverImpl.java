package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
        double[][] distanceMatrix = request.distanceMatrix();

        Map<DepotDto, List<CustomerDto>> customersByDepot = assignCustomersToNearestDepots(
                customers, depots, distanceMatrix
        );

        List<RouteDto> routes = new ArrayList<>();
        double totalCost = 0.0;
        for (Map.Entry<DepotDto, List<CustomerDto>> entry : customersByDepot.entrySet()) {
            DepotDto depot = entry.getKey();
            List<CustomerDto> assignedCustomers = entry.getValue();
            RouteDto route = buildRouteForDepot(depot, assignedCustomers, distanceMatrix);
            routes.add(route);
            totalCost += route.totalDistance();
        }

        long computationTime = System.currentTimeMillis() - startTime;

        return new RoutingResponse(
                request.problemId(),
                "COMPLETED",
                TypeSolver.RANDOM,
                totalCost,
                computationTime,
                routes
        );
    }

    private Map<DepotDto, List<CustomerDto>> assignCustomersToNearestDepots(
            List<CustomerDto> customers,
            List<DepotDto> depots,
            double[][] distanceMatrix
    ) {
        Map<DepotDto, List<CustomerDto>> customersByDepot = new HashMap<>();

        for (DepotDto depot : depots) {
            customersByDepot.put(depot, new ArrayList<>());
        }

        for (CustomerDto customer : customers) {
            DepotDto nearestDepot = findNearestDepot(customer, depots, distanceMatrix);
            customersByDepot.get(nearestDepot).add(customer);
        }

        return customersByDepot;
    }

    private DepotDto findNearestDepot(
            CustomerDto customer,
            List<DepotDto> depots,
            double[][] distanceMatrix
    ) {
        DepotDto nearestDepot = depots.get(0);
        double minDistance = distanceMatrix[nearestDepot.matrixIndex()][customer.matrixIndex()];

        for (DepotDto depot : depots) {
            double distance = distanceMatrix[depot.matrixIndex()][customer.matrixIndex()];
            if (distance < minDistance) {
                minDistance = distance;
                nearestDepot = depot;
            }
        }

        return nearestDepot;
    }

    private RouteDto buildRouteForDepot(DepotDto depot, List<CustomerDto> customers, double[][] distanceMatrix) {
        List<String> stops = new ArrayList<>();
        double totalDistance = 0.0;
        int totalLoad = 0;

        if (customers.isEmpty()) {
            String vehicleId = "V-" + depot.id();
            return new RouteDto(vehicleId, depot.id(), stops, 0.0, 0);
        }

        int currentIndex = depot.matrixIndex();

        for (CustomerDto customer : customers) {
            int customerIndex = customer.matrixIndex();
            totalDistance += distanceMatrix[currentIndex][customerIndex];
            stops.add(customer.id());
            totalLoad += customer.demand();
            currentIndex = customerIndex;
        }

        totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];

        String vehicleId = "V-" + depot.id();
        return new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad);
    }

    @Override
    public String getSolverId() {
        return "random";
    }
}
