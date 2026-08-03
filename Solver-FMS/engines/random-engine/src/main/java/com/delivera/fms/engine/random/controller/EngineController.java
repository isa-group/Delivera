package com.delivera.fms.engine.random.controller;

import com.delivera.fms.engine.random.dto.RoutingRequest;
import com.delivera.fms.engine.random.dto.RoutingResponse;
import com.delivera.fms.engine.random.service.RandomRouteSolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

    private final RandomRouteSolver solver;

    public EngineController(RandomRouteSolver solver) {
        this.solver = solver;
    }

    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(@Valid @RequestBody RoutingRequest request) {
        RoutingResponse response = solver.solve(request);
        return ResponseEntity.ok(response);
    }
}
