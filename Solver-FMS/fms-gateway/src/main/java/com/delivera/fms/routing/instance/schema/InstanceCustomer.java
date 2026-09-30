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
        description = "Customer of an MD-CVRP instance")
public record InstanceCustomer(

        @Schema(description = "Identifier of the customer's node", example = "5")
        @JsonProperty("id") int id,

        @Schema(description = "X coordinate of the customer's location", example = "37.5")
        @JsonProperty("x") double x,

        @Schema(description = "Y coordinate of the customer's location", example = "-2.1")
        @JsonProperty("y") double y,

        @Schema(description = "Service duration at the customer", example = "10")
        @JsonProperty("service_duration") int serviceDuration,

        @Schema(description = "Customer demand (units to deliver)", example = "12")
        @JsonProperty("demand") int demand,

        @Schema(description = "Visit frequency", example = "1")
        @JsonProperty("visit_frequency") int visitFrequency,

        @Schema(description = "Number of possible visit combinations", example = "0")
        @JsonProperty("num_combinations") int numCombinations,

        @ArraySchema(
                arraySchema = @Schema(description = "Visit combinations (present only if num_combinations > 0)"),
                schema = @Schema(implementation = Integer.class))
        @JsonProperty("visit_combinations") List<Integer> visitCombinations,

        @Schema(description = "Start of the time window (present only in variants with time windows)", example = "0")
        @JsonProperty("time_window_earliest") Integer timeWindowEarliest,

        @Schema(description = "End of the time window (present only in variants with time windows)", example = "1000")
        @JsonProperty("time_window_latest") Integer timeWindowLatest
) {
}
