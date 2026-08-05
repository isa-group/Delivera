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
        description = "Estructura del fichero de instancia de benchmark MD-CVRP en formato JSON")
public record ProblemInstance(

        @Schema(description = "Nombre del fichero de la instancia", example = "p01.json")
        @JsonProperty("filename") String filename,

        @Schema(description = "Nombre legible del tipo de problema", example = "MDVRP")
        @JsonProperty("problem_type") String problemType,

        @Schema(description = "Codigo numerico del tipo de problema (0=VRP, 2=MDVRP, ...)", example = "2")
        @JsonProperty("problem_type_code") int problemTypeCode,

        @Schema(description = "Numero de vehiculos disponibles por deposito", example = "4")
        @JsonProperty("vehicles_per_depot") int vehiclesPerDepot,

        @Schema(description = "Numero total de clientes de la instancia", example = "50")
        @JsonProperty("num_customers") int numCustomers,

        @Schema(description = "Numero total de depositos de la instancia", example = "4")
        @JsonProperty("num_depots") int numDepots,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de depositos de la instancia"),
                schema = @Schema(implementation = InstanceDepot.class))
        @JsonProperty("depots") List<InstanceDepot> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de clientes de la instancia"),
                schema = @Schema(implementation = InstanceCustomer.class))
        @JsonProperty("customers") List<InstanceCustomer> customers
) {
}
