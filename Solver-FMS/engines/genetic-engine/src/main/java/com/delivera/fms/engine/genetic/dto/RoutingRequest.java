package com.delivera.fms.engine.genetic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record RoutingRequest(
        @NotBlank String problemId,
        @NotEmpty @Valid List<DepotDto> depots,
        @NotEmpty @Valid List<CustomerDto> customers,
        @Valid List<VehicleDto> vehicles,
        @NotNull double[][] distanceMatrix,

        /**
         * Parametros del algoritmo. La pasarela los envia ya resueltos contra los
         * metadatos del solver; si se llama al motor directamente pueden venir
         * incompletos o vacios, y cada ausencia toma el valor por defecto de
         * {@link GeneticParameters}.
         */
        Map<String, Object> parameters
) {
}
