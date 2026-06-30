package com.delivera.fms.dto;

import java.util.List;

public record RoutingResponse(
        String problemId,
        String status,
        TypeSolver solverUsed,
        Double totalCost,
        Long computationTimeMs,
        List<RouteDto> routes
) {
}
