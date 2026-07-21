package com.delivera.fms.engine.random.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepotDto(
        @NotBlank String id,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Integer matrixIndex
) {
}
