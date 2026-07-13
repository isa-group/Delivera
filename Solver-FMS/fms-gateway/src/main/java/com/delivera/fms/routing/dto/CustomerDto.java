package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos de un cliente (punto de entrega) para el problema de ruteo")
public record CustomerDto(
        @Schema(description = "Identificador unico del cliente", example = "C-001")
        @NotBlank String id,

        @Schema(description = "Demanda del cliente (unidades a entregar)", example = "5")
        @NotNull @Min(value = 1) Integer demand,

        @Schema(description = "Latitud de la ubicacion del cliente", example = "40.4168")
        @NotNull Double lat,

        @Schema(description = "Longitud de la ubicacion del cliente", example = "-3.7038")
        @NotNull Double lng,

        @Schema(description = "Indice del cliente en la matriz de distancias", example = "0")
        @NotNull Integer matrixIndex
) {
}
