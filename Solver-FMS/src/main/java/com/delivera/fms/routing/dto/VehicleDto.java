package com.delivera.fms.routing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Unused for now, but we will need it when we have vehicles in the request
public record VehicleDto(
        // @NotBlank
        // String id,

        // @NotNull
        // @Min(value = 1)
        // Integer capacity,

        // @NotBlank
        // String startDepotId
) {
}