package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RouteDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.dto.VehicleDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GreedyRouteSolverImplTest {

    @Test
    void shouldSolveSimpleProblem() {
        DepotDto depot = new DepotDto("D1", 0.0, 0.0, 0);
        CustomerDto c1 = new CustomerDto("C1", 10, 1.0, 0.0, 1);
        CustomerDto c2 = new CustomerDto("C2", 10, 0.0, 1.0, 2);

        double[][] matrix = {
                {0, 1, 1},
                {1, 0, 1.414},
                {1, 1.414, 0}
        };

        RoutingRequest request = new RoutingRequest(
                "TEST-001",
                List.of(depot),
                List.of(c1, c2),
                null,
                matrix,
                TypeSolver.GREEDY
        );

        GreedyRouteSolverImpl solver = new GreedyRouteSolverImpl();
        RoutingResponse response = solver.solve(request);

        assertNotNull(response);
        assertEquals("TEST-001", response.problemId());
        assertEquals("COMPLETED", response.status());
        assertEquals(TypeSolver.GREEDY, response.solverUsed());
        assertFalse(response.routes().isEmpty());

        Set<String> allStops = response.routes().stream()
                .flatMap(r -> r.stops().stream())
                .collect(Collectors.toSet());
        assertTrue(allStops.contains("C1"));
        assertTrue(allStops.contains("C2"));
    }

    @Test
    void shouldRespectVehicleCapacity() {
        DepotDto depot = new DepotDto("D1", 0.0, 0.0, 0);
        CustomerDto c1 = new CustomerDto("C1", 50, 1.0, 0.0, 1);
        CustomerDto c2 = new CustomerDto("C2", 50, 0.0, 1.0, 2);

        double[][] matrix = {
                {0, 1, 1},
                {1, 0, 1.414},
                {1, 1.414, 0}
        };

        VehicleDto vehicle = new VehicleDto("V1", 60, "D1");

        RoutingRequest request = new RoutingRequest(
                "TEST-002",
                List.of(depot),
                List.of(c1, c2),
                List.of(vehicle),
                matrix,
                TypeSolver.GREEDY
        );

        GreedyRouteSolverImpl solver = new GreedyRouteSolverImpl();
        RoutingResponse response = solver.solve(request);

        assertNotNull(response);
        for (RouteDto route : response.routes()) {
            assertTrue(route.totalLoad() <= 60);
        }
    }

    @Test
    void shouldHandleMultipleDepots() {
        DepotDto d1 = new DepotDto("D1", 0.0, 0.0, 0);
        DepotDto d2 = new DepotDto("D2", 10.0, 10.0, 1);
        CustomerDto c1 = new CustomerDto("C1", 10, 0.5, 0.5, 2);
        CustomerDto c2 = new CustomerDto("C2", 10, 9.5, 9.5, 3);

        double[][] matrix = {
                {0, 14.14, 0.71, 13.44},
                {14.14, 0, 13.44, 0.71},
                {0.71, 13.44, 0, 12.73},
                {13.44, 0.71, 12.73, 0}
        };

        RoutingRequest request = new RoutingRequest(
                "TEST-003",
                List.of(d1, d2),
                List.of(c1, c2),
                null,
                matrix,
                TypeSolver.GREEDY
        );

        GreedyRouteSolverImpl solver = new GreedyRouteSolverImpl();
        RoutingResponse response = solver.solve(request);

        assertNotNull(response);
        assertFalse(response.routes().isEmpty());
    }
}
