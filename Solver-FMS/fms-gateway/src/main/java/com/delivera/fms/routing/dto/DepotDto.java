package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Depot", description = "A depot (starting point of vehicles)")
public record DepotDto(
        @Schema(description = "Unique depot identifier", example = "DEP-1")
        @NotBlank String id,

        @Schema(description = "Latitude of the depot's location", example = "40.4168")
        @NotNull Double lat,

        @Schema(description = "Longitude of the depot's location", example = "-3.7038")
        @NotNull Double lng,

        @Schema(description = "Index of the depot in the distance matrix", example = "0")
        @NotNull Integer matrixIndex,

        @Schema(description = "Maximum duration of a route leaving from this depot, in the same "
                + "units as the distance matrix. Absent or 0 means no limit",
                example = "200")
        Double maxDuration
) {
}
