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
        description = "Instancia de benchmark MD-CVRP con sus nodos, en el modelo que recibe el motor")
public record InstanceDetail(

        @Schema(description = "Propiedades de la instancia, las mismas que devuelve el catalogo")
        InstanceSummary summary,

        @ArraySchema(
                arraySchema = @Schema(description = "Depositos, numerados por posicion empezando en 1"),
                schema = @Schema(implementation = DepotDto.class))
        List<DepotDto> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "Clientes a servir, con el identificador del fichero"),
                schema = @Schema(implementation = CustomerDto.class))
        List<CustomerDto> customers,

        @ArraySchema(
                arraySchema = @Schema(description = "Flota derivada de la instancia: "
                        + "vehiclesPerDepot vehiculos por cada deposito"),
                schema = @Schema(implementation = VehicleDto.class))
        List<VehicleDto> vehicles
) {
}
