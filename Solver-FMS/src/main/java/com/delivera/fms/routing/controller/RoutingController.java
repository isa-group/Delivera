package com.delivera.fms.routing.controller;

import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.service.RouteSolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fms/routing")
public class RoutingController {

    private final RouteSolver routeSolver;

    public RoutingController(RouteSolver routeSolver) {
        this.routeSolver = routeSolver;
    }

    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(@Valid @RequestBody RoutingRequest request) {
        RoutingResponse response = routeSolver.solve(request);
        return ResponseEntity.ok(response);
    }
}
