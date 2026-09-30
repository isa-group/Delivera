package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Vehicle", description = "A vehicle available to the route solver")
public record VehicleDto(
        @Schema(description = "Unique vehicle identifier", example = "V-001")
        @NotBlank String id,

        @Schema(description = "Maximum load capacity of the vehicle", example = "100")
        @NotNull @Min(value = 1) Integer capacity,

        @Schema(description = "ID of the home depot assigned to the vehicle", example = "DEP-1")
        @NotBlank String startDepotId
) {
}
