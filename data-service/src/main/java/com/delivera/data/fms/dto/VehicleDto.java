package com.delivera.data.fms.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "Datos de un vehiculo para el solver de rutas")
public class VehicleDto {
        @NotNull
        @Schema(description = "Identificador unico del vehiculo", example = "V-001")
        private UUID id;

        @NotNull
        @Schema(description = "Capacidad maxima de carga del vehiculo", example = "100")
        @Min(1)
        private Integer capacity;

        @NotNull
        @Schema(description = "ID del deposito de origen asignado al vehiculo", example = "DEP-1")
        private UUID startDepotId;

}
