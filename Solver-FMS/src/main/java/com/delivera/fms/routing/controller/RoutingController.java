package com.delivera.fms.routing.controller;

import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.service.RouteSolver;
import com.delivera.fms.routing.service.RouteSolverFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fms/routing")
@Tag(name = "Routing", description = "Resolucion de rutas de vehiculos")
public class RoutingController {

    private final RouteSolverFactory solverFactory;

    public RoutingController(RouteSolverFactory solverFactory) {
        this.solverFactory = solverFactory;
    }

    @Operation(summary = "Resolver problema de ruteo",
            description = "Resuelve un problema de ruteo de vehiculos con capacidad para múltiples almacenes (MD-CVRP) " +
                    "utilizando el solver especificado en la solicitud. " +
                    "Devuelve las rutas optimizadas con sus vehiculos, paradas, distancias y cargas.")
    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(@Valid @RequestBody RoutingRequest request) {
        validateConsistency(request);
        RouteSolver solver = solverFactory.getSolver(request.solverType());
        RoutingResponse response = solver.solve(request);
        return ResponseEntity.ok(response);
    }

    private void validateConsistency(RoutingRequest request) {
        int expectedSize = request.depots().size() + request.customers().size();
        double[][] matrix = request.distanceMatrix();

        if (matrix.length != expectedSize) {
            throw new IllegalArgumentException(
                    "Distance matrix rows (" + matrix.length + ") must equal depots + customers (" + expectedSize + ")");
        }
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i].length != expectedSize) {
                throw new IllegalArgumentException(
                        "Distance matrix column " + i + " size (" + matrix[i].length + ") must equal " + expectedSize);
            }
        }

        Set<Integer> indices = request.depots().stream()
                .map(d -> d.matrixIndex())
                .collect(Collectors.toSet());
        for (var c : request.customers()) {
            if (!indices.add(c.matrixIndex())) {
                throw new IllegalArgumentException("Duplicate matrixIndex found: " + c.matrixIndex());
            }
        }
        for (int idx : indices) {
            if (idx < 0 || idx >= expectedSize) {
                throw new IllegalArgumentException("matrixIndex out of range: " + idx);
            }
        }

        if (request.vehicles() != null) {
            Set<String> depotIds = request.depots().stream()
                    .map(d -> d.id())
                    .collect(Collectors.toSet());
            for (var v : request.vehicles()) {
                if (!depotIds.contains(v.startDepotId())) {
                    throw new IllegalArgumentException("Vehicle " + v.id() + " references unknown depot: " + v.startDepotId());
                }
            }
        }
    }
}
