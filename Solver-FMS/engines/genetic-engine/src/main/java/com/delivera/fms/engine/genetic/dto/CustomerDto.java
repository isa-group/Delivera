package com.delivera.fms.engine.genetic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerDto(
        @NotBlank String id,
        @NotNull @Min(value = 1) Integer demand,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Integer matrixIndex
) {
}
