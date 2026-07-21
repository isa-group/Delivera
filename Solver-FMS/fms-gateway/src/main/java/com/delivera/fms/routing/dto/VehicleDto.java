package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos de un vehiculo para el solver de rutas")
public record VehicleDto(
        @Schema(description = "Identificador unico del vehiculo", example = "V-001")
        @NotBlank String id,

        @Schema(description = "Capacidad maxima de carga del vehiculo", example = "100")
        @NotNull @Min(value = 1) Integer capacity,

        @Schema(description = "ID del deposito de origen asignado al vehiculo", example = "DEP-1")
        @NotBlank String startDepotId
) {
}
