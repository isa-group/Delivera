package com.delivera.fms.routing.instance.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Representacion de documentacion (OpenAPI) del formato del fichero de instancia
 * MD-CVRP en JSON, tal como lo genera el parser de instancias. No se utiliza para
 * (de)serializacion en tiempo de ejecucion; su unico proposito es documentar la
 * estructura de la instancia en la especificacion OpenAPI.
 */
@Schema(name = "ProblemInstance",
        description = "Structure of the MD-CVRP benchmark instance file in JSON format")
public record ProblemInstance(

        @Schema(description = "Instance file name", example = "p01.json")
        @JsonProperty("filename") String filename,

        @Schema(description = "Human-readable problem type name", example = "MDVRP")
        @JsonProperty("problem_type") String problemType,

        @Schema(description = "Numeric problem type code (0=VRP, 2=MDVRP, ...)", example = "2")
        @JsonProperty("problem_type_code") int problemTypeCode,

        @Schema(description = "Number of vehicles available per depot", example = "4")
        @JsonProperty("vehicles_per_depot") int vehiclesPerDepot,

        @Schema(description = "Total number of customers in the instance", example = "50")
        @JsonProperty("num_customers") int numCustomers,

        @Schema(description = "Total number of depots in the instance", example = "4")
        @JsonProperty("num_depots") int numDepots,

        @ArraySchema(
                arraySchema = @Schema(description = "List of the instance's depots"),
                schema = @Schema(implementation = InstanceDepot.class))
        @JsonProperty("depots") List<InstanceDepot> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "List of the instance's customers"),
                schema = @Schema(implementation = InstanceCustomer.class))
        @JsonProperty("customers") List<InstanceCustomer> customers
) {
}
