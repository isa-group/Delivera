package com.delivera.fms.engine.annealing.controller;

import com.delivera.fms.engine.annealing.dto.RoutingRequest;
import com.delivera.fms.engine.annealing.dto.RoutingResponse;
import com.delivera.fms.engine.annealing.service.AnnealingRouteSolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

    private final AnnealingRouteSolver solver;

    public EngineController(AnnealingRouteSolver solver) {
        this.solver = solver;
    }

    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(@Valid @RequestBody RoutingRequest request) {
        RoutingResponse response = solver.solve(request);
        return ResponseEntity.ok(response);
    }
}
