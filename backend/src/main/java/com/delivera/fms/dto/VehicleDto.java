package com.delivera.fms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleDto(
        @NotBlank
        String id,

        @NotNull
        @Min(1)
        Integer capacity,

        @NotBlank
        String startDepotId
) {
}
