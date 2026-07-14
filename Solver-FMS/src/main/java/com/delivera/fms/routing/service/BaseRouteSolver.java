package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseRouteSolver implements RouteSolver {

    protected abstract TypeSolver getType();

    protected abstract List<RouteDto> performRouting(RoutingRequest request);


    @Override
    public final RoutingResponse solve(RoutingRequest request) {
        long startTime = System.currentTimeMillis();
        List<RouteDto> routes = performRouting(request);
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;
        return new RoutingResponse(
                request.problemId(), "COMPLETED", getType(),
                totalCost, computationTime, routes
        );
    }

    @Override
    public String getSolverId() {
        return getType().name().toLowerCase();
    }

    protected Map<DepotDto, List<CustomerDto>> groupByNearestDepot(
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

    protected void addRoute(List<RouteDto> routes, RouteDto route) {
        if (route != null && !route.stops().isEmpty()) {
            routes.add(route);
        }
    }
}
