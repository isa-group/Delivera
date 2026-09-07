package com.delivera.data.fms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de un deposito (punto de origen de vehiculos)")
public record DepotDto(
        @Schema(description = "Identificador unico del deposito", example = "DEP-1")
        String id,

        @Schema(description = "Latitud de la ubicacion del deposito", example = "40.4168")
        Double lat,

        @Schema(description = "Longitud de la ubicacion del deposito", example = "-3.7038")
        Double lng,

        @Schema(description = "Indice del deposito en la matriz de distancias", example = "0")
        Integer matrixIndex
)implements Coordinates{

        @Override
        public Double getLat() {
                return this.lat();
        }

        @Override
        public Double getLng() {
                return this.lng();
        }
}
