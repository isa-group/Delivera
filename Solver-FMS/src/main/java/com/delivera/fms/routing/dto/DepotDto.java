package com.delivera.fms.routing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepotDto(
        @NotBlank
        String id,

        @NotNull
        Double lat,

        @NotNull
        Double lng
) {
}