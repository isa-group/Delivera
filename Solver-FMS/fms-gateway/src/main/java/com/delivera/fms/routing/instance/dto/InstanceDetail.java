package com.delivera.fms.routing.instance.dto;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.VehicleDto;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Una instancia completa, ya traducida al modelo del gateway.
 *
 * No devuelve el fichero tal cual: devuelve los depositos, clientes y vehiculos
 * que se le enviarian al motor, con los mismos identificadores con los que luego
 * aparecen en las rutas de la solucion. Es lo que permite leer una respuesta de
 * ruteo contra la instancia que la origino.
 */
@Schema(name = "InstanceDetail",
        description = "MD-CVRP benchmark instance with its nodes, in the model the engine receives")
public record InstanceDetail(

        @Schema(description = "Instance properties, the same ones the catalogue returns")
        InstanceSummary summary,

        @ArraySchema(
                arraySchema = @Schema(description = "Depots, numbered by position starting at 1"),
                schema = @Schema(implementation = DepotDto.class))
        List<DepotDto> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "Customers to serve, with the identifier from the file"),
                schema = @Schema(implementation = CustomerDto.class))
        List<CustomerDto> customers,

        @ArraySchema(
                arraySchema = @Schema(description = "Fleet derived from the instance: "
                        + "vehiclesPerDepot vehicles per depot"),
                schema = @Schema(implementation = VehicleDto.class))
        List<VehicleDto> vehicles
) {
}
