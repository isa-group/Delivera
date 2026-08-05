package com.delivera.fms.engine.greedy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerDto(
        @NotBlank String id,
        @NotNull @Min(value = 1) Integer demand,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Integer matrixIndex,
        // Tiempo de servicio en el cliente. Nulo significa 0. Consume duracion de
        // ruta pero no suma al coste.
        Double serviceDuration
) {

    public double service() {
        return (serviceDuration == null) ? 0.0 : serviceDuration;
    }
}
