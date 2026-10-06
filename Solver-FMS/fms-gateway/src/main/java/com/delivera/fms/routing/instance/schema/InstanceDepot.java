package com.delivera.fms.routing.instance.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Representacion de documentacion (OpenAPI) de un deposito dentro del fichero de
 * instancia MD-CVRP en JSON.
 */
@Schema(name = "InstanceDepot",
        title = "InstanceDepot",
        description = "Depot of an MD-CVRP instance (configuration and coordinates)")
public record InstanceDepot(

        @Schema(description = "Depot identifier within the instance", example = "1")
        @JsonProperty("depot_id") int depotId,

        @Schema(description = "Maximum allowed route duration (0 = no limit)", example = "0")
        @JsonProperty("max_duration") int maxDuration,

        @Schema(description = "Load capacity of the depot's vehicles", example = "80")
        @JsonProperty("vehicle_capacity") int vehicleCapacity,

        @Schema(description = "Identifier of the depot's node", example = "1")
        @JsonProperty("id") int id,

        @Schema(description = "X coordinate of the depot's location", example = "40.5")
        @JsonProperty("x") double x,

        @Schema(description = "Y coordinate of the depot's location", example = "-3.7")
        @JsonProperty("y") double y,

        @Schema(description = "Service duration at the depot", example = "0")
        @JsonProperty("service_duration") int serviceDuration,

        @Schema(description = "Depot demand (usually 0)", example = "0")
        @JsonProperty("demand") int demand,

        @Schema(description = "Visit frequency", example = "0")
        @JsonProperty("visit_frequency") int visitFrequency
) {
}
