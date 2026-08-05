package com.delivera.data.fms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de un cliente (punto de entrega) para el problema de ruteo")
public record CustomerDto(
        @Schema(description = "Identificador unico del cliente", example = "C-001")
        String id,

        @Schema(description = "Demanda del cliente (unidades a entregar)", example = "5")
        Integer demand,

        @Schema(description = "Latitud de la ubicacion del cliente", example = "40.4168")
        Double lat,

        @Schema(description = "Longitud de la ubicacion del cliente", example = "-3.7038")
        Double lng,

        @Schema(description = "Indice del cliente en la matriz de distancias", example = "0")
        Integer matrixIndex
) {
}
