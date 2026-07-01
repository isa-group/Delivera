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
        RouteSolver solver = solverFactory.getSolver(request.solverType());
        RoutingResponse response = solver.solve(request);
        return ResponseEntity.ok(response);
    }
}
