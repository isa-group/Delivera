package com.delivera.fms.engine.random.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepotDto(
        @NotBlank String id,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Integer matrixIndex,
        // Duracion maxima de una ruta del deposito. Nula o 0 significa sin limite.
        Double maxDuration
) {

    public double durationLimit() {
        return (maxDuration == null || maxDuration <= 0) ? Double.MAX_VALUE : maxDuration;
    }
}
