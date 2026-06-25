package com.delivera.fms.routing.controller;

import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.service.RouteSolver;
import com.delivera.fms.routing.service.RouteSolverFactory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fms/routing")
public class RoutingController {

    private final RouteSolverFactory solverFactory;

    public RoutingController(RouteSolverFactory solverFactory) {
        this.solverFactory = solverFactory;
    }

    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(@Valid @RequestBody RoutingRequest request) {
        RouteSolver solver = solverFactory.getSolver(request.solverType());
        RoutingResponse response = solver.solve(request);
        return ResponseEntity.ok(response);
    }
}
