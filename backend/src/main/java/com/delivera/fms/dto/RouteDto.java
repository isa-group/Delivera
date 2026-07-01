package com.delivera.fms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ruta asignada a un vehiculo con sus paradas y metricas")
public record RouteDto(
        @Schema(description = "ID del vehiculo que realiza la ruta", example = "V-001")
        String vehicleId,

        @Schema(description = "ID del deposito de origen de la ruta", example = "DEP-1")
        String depotId,

        @Schema(description = "Lista ordenada de IDs de clientes (paradas) de la ruta",
                example = "[\"C-001\", \"C-003\", \"C-005\"]")
        List<String> stops,

        @Schema(description = "Distancia total recorrida en la ruta (en km)", example = "125.5")
        Double totalDistance,

        @Schema(description = "Carga total transportada en la ruta (suma de demandas)", example = "85")
        Integer totalLoad
) {
}
