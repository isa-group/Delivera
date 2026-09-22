package com.delivera.fms.engine.random.dto;

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
         * Parametros de invocacion. El unico que admite este motor es 'seed'; si no
         * llega, el solver sortea una semilla y la devuelve en la respuesta.
         */
        Map<String, Object> parameters
) {
}
