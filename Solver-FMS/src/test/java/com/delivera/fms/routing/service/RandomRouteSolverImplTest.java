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

class RandomRouteSolverImplTest {

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
                TypeSolver.RANDOM
        );

        RandomRouteSolverImpl solver = new RandomRouteSolverImpl();
        RoutingResponse response = solver.solve(request);

        assertNotNull(response);
        assertEquals("TEST-001", response.problemId());
        assertEquals("COMPLETED", response.status());
        assertEquals(TypeSolver.RANDOM, response.solverUsed());
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
                TypeSolver.RANDOM
        );

        RandomRouteSolverImpl solver = new RandomRouteSolverImpl();
        RoutingResponse response = solver.solve(request);

        assertNotNull(response);
        for (RouteDto route : response.routes()) {
            assertTrue(route.totalLoad() <= 60);
        }
    }

}
