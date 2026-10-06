package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "Route", description = "Route assigned to a vehicle, with its stops and metrics")
public record RouteDto(
        @Schema(description = "ID of the vehicle driving the route", example = "V-001")
        @NotBlank String vehicleId,

        @Schema(description = "ID of the route's home depot", example = "DEP-1")
        String depotId,

        @Schema(description = "Ordered list of customer IDs (stops) of the route",
                example = "[\"C-001\", \"C-003\", \"C-005\"]")
        @NotEmpty List<String> stops,

        @Schema(description = "Total distance travelled on the route, in the units of the distance matrix",
                example = "125.5")
        @NotNull Double totalDistance,

        @Schema(description = "Total load carried on the route (sum of demands)", example = "85")
        @NotNull Integer totalLoad
) {
}
