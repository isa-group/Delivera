package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos de un deposito (punto de origen de vehiculos)")
public record DepotDto(
        @Schema(description = "Identificador unico del deposito", example = "DEP-1")
        @NotBlank String id,

        @Schema(description = "Latitud de la ubicacion del deposito", example = "40.4168")
        @NotNull Double lat,

        @Schema(description = "Longitud de la ubicacion del deposito", example = "-3.7038")
        @NotNull Double lng,

        @Schema(description = "Indice del deposito en la matriz de distancias", example = "0")
        @NotNull Integer matrixIndex
) {
}
