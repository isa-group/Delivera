package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TracePoint", description = "Un punto de la curva anytime de un solver")
public record TracePointDto(
        @Schema(description = "Milisegundos desde el arranque del motor", example = "1780")
        Long elapsedMs,

        @Schema(description = "Coste de la mejor solucion factible en ese instante", example = "5901.44")
        Double cost
) {
}
