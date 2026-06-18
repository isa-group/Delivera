package com.delivera.fms.dto;

import java.util.List;

public record RoutingResponse(
        String problemId,
        String status,
        TypeSolver solverUsed,
        Long computationTimeMs,
        List<RouteDto> routes
) {
}
