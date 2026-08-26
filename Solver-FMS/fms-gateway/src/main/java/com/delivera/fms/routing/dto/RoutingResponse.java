package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Respuesta con la solucion del problema de ruteo")
public record RoutingResponse(
        @Schema(description = "Identificador del problema resuelto", example = "p01")
        String problemId,

        @Schema(description = "Estado de la resolucion (COMPLETED / FAILURE)", example = "COMPLETED")
        String status,

        @Schema(description = "Tipo de solver utilizado en la resolucion",
                allowableValues = {"RANDOM", "GREEDY", "GENETIC"}, example = "GREEDY")
        String solverUsed,

        @Schema(description = "Costo total de la solucion (suma de distancias de todas las rutas)", example = "775.2683743807665")
        Double totalCost,

        @Schema(description = "Tiempo de computo en milisegundos", example = "6")
        Long computationTimeMs,

        @Schema(description = "Semilla con la que se ejecuto el solver. Reenviarla en " +
                "'parameters' reproduce exactamente esta solucion. Ausente en los solvers " +
                "deterministas, que no dependen del azar",
                example = "1234", nullable = true)
        Long seed,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de rutas que componen la solucion"),
                schema = @Schema(implementation = RouteDto.class))
        List<RouteDto> routes
) {
}
