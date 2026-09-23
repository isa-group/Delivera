package com.delivera.fms.engine.annealing.dto;

import java.util.List;

public record RoutingResponse(
        String problemId,
        String status,
        String solverUsed,
        Double totalCost,
        Long computationTimeMs,

        // Semilla efectiva de la ejecucion. Reenviarla reproduce esta misma solucion
        Long seed,

        List<RouteDto> routes,

        /**
         * Curva anytime: cada instante en que mejoro la mejor solucion factible. Permite saber
         * que coste habria dado el motor con menos presupuesto sin volver a ejecutarlo.
         */
        List<TracePointDto> trace
) {
}
