package com.delivera.fms.routing.instance.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Representacion de documentacion (OpenAPI) de un deposito dentro del fichero de
 * instancia MD-CVRP en JSON.
 */
@Schema(name = "InstanceDepot",
        title = "InstanceDepot",
        description = "Deposito de una instancia MD-CVRP (configuracion y coordenadas)")
public record InstanceDepot(

        @Schema(description = "Identificador del deposito dentro de la instancia", example = "1")
        @JsonProperty("depot_id") int depotId,

        @Schema(description = "Duracion maxima de ruta permitida (0 = sin limite)", example = "0")
        @JsonProperty("max_duration") int maxDuration,

        @Schema(description = "Capacidad de carga de los vehiculos del deposito", example = "80")
        @JsonProperty("vehicle_capacity") int vehicleCapacity,

        @Schema(description = "Identificador del nodo del deposito", example = "1")
        @JsonProperty("id") int id,

        @Schema(description = "Coordenada X de la ubicacion del deposito", example = "40.5")
        @JsonProperty("x") double x,

        @Schema(description = "Coordenada Y de la ubicacion del deposito", example = "-3.7")
        @JsonProperty("y") double y,

        @Schema(description = "Duracion del servicio en el deposito", example = "0")
        @JsonProperty("service_duration") int serviceDuration,

        @Schema(description = "Demanda del deposito (normalmente 0)", example = "0")
        @JsonProperty("demand") int demand,

        @Schema(description = "Frecuencia de visita", example = "0")
        @JsonProperty("visit_frequency") int visitFrequency
) {
}
