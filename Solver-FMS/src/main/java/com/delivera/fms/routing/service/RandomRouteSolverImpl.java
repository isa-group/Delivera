package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service("randomRouteSolver")
public class RandomRouteSolverImpl implements RouteSolver {

    // No está implementado, solo devuelve un RoutingResponse con status "PENDING_IMPLEMENTATION"
    @Override
    public RoutingResponse solve(RoutingRequest request) {
        return new RoutingResponse(
                request.problemId(),
                "PENDING_IMPLEMENTATION",
                TypeSolver.RANDOM,
                0L,
                Collections.emptyList()
        );
    }

    @Override
    public String getSolverId() {
        return "random";
    }
}
