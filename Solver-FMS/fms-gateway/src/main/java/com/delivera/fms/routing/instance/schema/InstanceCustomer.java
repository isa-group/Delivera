package com.delivera.fms.routing.instance.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Representacion de documentacion (OpenAPI) de un cliente dentro del fichero de
 * instancia MD-CVRP en JSON.
 */
@Schema(name = "InstanceCustomer",
        title = "InstanceCustomer",
        description = "Cliente de una instancia MD-CVRP")
public record InstanceCustomer(

        @Schema(description = "Identificador del nodo del cliente", example = "5")
        @JsonProperty("id") int id,

        @Schema(description = "Coordenada X de la ubicacion del cliente", example = "37.5")
        @JsonProperty("x") double x,

        @Schema(description = "Coordenada Y de la ubicacion del cliente", example = "-2.1")
        @JsonProperty("y") double y,

        @Schema(description = "Duracion del servicio en el cliente", example = "10")
        @JsonProperty("service_duration") int serviceDuration,

        @Schema(description = "Demanda del cliente (unidades a entregar)", example = "12")
        @JsonProperty("demand") int demand,

        @Schema(description = "Frecuencia de visita", example = "1")
        @JsonProperty("visit_frequency") int visitFrequency,

        @Schema(description = "Numero de combinaciones de visita posibles", example = "0")
        @JsonProperty("num_combinations") int numCombinations,

        @ArraySchema(
                arraySchema = @Schema(description = "Combinaciones de visita (presente solo si num_combinations > 0)"),
                schema = @Schema(implementation = Integer.class))
        @JsonProperty("visit_combinations") List<Integer> visitCombinations,

        @Schema(description = "Inicio de la ventana de tiempo (presente solo en variantes con ventanas)", example = "0")
        @JsonProperty("time_window_earliest") Integer timeWindowEarliest,

        @Schema(description = "Fin de la ventana de tiempo (presente solo en variantes con ventanas)", example = "1000")
        @JsonProperty("time_window_latest") Integer timeWindowLatest
) {
}
