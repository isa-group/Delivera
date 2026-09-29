package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Customer", description = "A customer (delivery point) of the routing problem")
public record CustomerDto(
        @Schema(description = "Unique customer identifier", example = "C-001")
        @NotBlank String id,

        @Schema(description = "Customer demand (units to deliver)", example = "5")
        @NotNull @Min(value = 1) Integer demand,

        @Schema(description = "Latitude of the customer's location", example = "40.4168")
        @NotNull Double lat,

        @Schema(description = "Longitude of the customer's location", example = "-3.7038")
        @NotNull Double lng,

        @Schema(description = "Index of the customer in the distance matrix", example = "0")
        @NotNull Integer matrixIndex,

        @Schema(description = "Service time at the customer, in the same units as the maximum "
                + "route duration. Absent means 0", example = "10")
        Double serviceDuration
) {
}
