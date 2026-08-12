package com.delivera.fms.engine.random.dto;

import java.util.List;

public record RoutingResponse(
        String problemId,
        String status,
        String solverUsed,
        Double totalCost,
        Long computationTimeMs,

        // Semilla efectiva de la ejecucion. Reenviarla reproduce esta misma solucion
        Long seed,

        List<RouteDto> routes
) {
}
