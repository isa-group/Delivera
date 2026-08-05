package com.delivera.data.fms.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Respuesta con la solucion del problema de ruteo")
public record RoutingResponse(
        @Schema(description = "Identificador del problema resuelto", example = "PROBLEM-001")
        String problemId,

        @Schema(description = "Estado de la resolucion (SUCCESS / FAILURE)", example = "SUCCESS")
        String status,

        @Schema(description = "Tipo de solver utilizado en la resolucion")
        TypeSolver solverUsed,

        @Schema(description = "Costo total de la solucion (suma de distancias de todas las rutas)",
                example = "450.75")
        Double totalCost,

        @Schema(description = "Tiempo de computo en milisegundos", example = "1250")
        Long computationTimeMs,

        @ArraySchema(schema = @Schema(description = "Lista de rutas que componen la solucion"))
        List<RouteDto> routes
) {
}
