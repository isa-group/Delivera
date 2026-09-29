package com.delivera.fms.routing.instance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ficha de una instancia estandar: lo que hace falta para elegirla sin
 * descargarla entera.
 *
 * Son propiedades del problema, no de ninguna solucion: describen su tamano y
 * lo apretado que esta, que es lo que separa a una instancia facil de una dura.
 * La lista de nodos vive en {@link InstanceDetail}.
 */
@Schema(name = "InstanceSummary",
        description = "Properties of an MD-CVRP benchmark instance, without its nodes")
public record InstanceSummary(

        @Schema(description = "Instance name, without extension. It is the value sent "
                + "as fileName to solve it", example = "p01")
        String name,

        @Schema(description = "Problem variant declared in the file", example = "MDVRP")
        String problemType,

        @Schema(description = "Number of depots", example = "4")
        int numDepots,

        @Schema(description = "Number of customers to serve", example = "50")
        int numCustomers,

        @Schema(description = "Vehicles available at each depot", example = "4")
        int vehiclesPerDepot,

        @Schema(description = "Load capacity of each vehicle. The bank's instances share it "
                + "across depots; the per-depot value is in the detail",
                example = "80")
        int vehicleCapacity,

        @Schema(description = "Maximum route duration, in the same units as the distances. "
                + "Absent means no limit: the file encodes it with a 0, which is not a "
                + "duration but the absence of the datum",
                example = "310")
        Double maxDuration,

        @Schema(description = "Sum of the demand of all customers", example = "777")
        int totalDemand,

        @Schema(description = "Fraction of the fleet's total capacity consumed by the demand. "
                + "Measures how tight the instance is: the closer to 1, the less slack "
                + "there is to distribute",
                example = "0.6070")
        double loadRatio
) {
}
