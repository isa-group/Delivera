package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TracePoint", description = "A point of a solver's anytime curve")
public record TracePointDto(
        @Schema(description = "Milliseconds since the engine started", example = "1780")
        Long elapsedMs,

        @Schema(description = "Cost of the best feasible solution at that instant", example = "5901.44")
        Double cost
) {
}
