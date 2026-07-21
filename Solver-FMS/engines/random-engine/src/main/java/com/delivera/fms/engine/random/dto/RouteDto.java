package com.delivera.fms.engine.random.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RouteDto(
        @NotBlank String vehicleId,
        String depotId,
        @NotEmpty List<String> stops,
        @NotNull Double totalDistance,
        @NotNull Integer totalLoad
) {
}
