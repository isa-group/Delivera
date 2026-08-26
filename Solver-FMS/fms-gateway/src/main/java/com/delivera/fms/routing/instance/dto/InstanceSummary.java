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
        description = "Propiedades de una instancia de benchmark MD-CVRP, sin sus nodos")
public record InstanceSummary(

        @Schema(description = "Nombre de la instancia, sin extension. Es el valor que se envia "
                + "como fileName al resolverla", example = "p01")
        String name,

        @Schema(description = "Variante del problema declarada en el fichero", example = "MDVRP")
        String problemType,

        @Schema(description = "Numero de depositos", example = "4")
        int numDepots,

        @Schema(description = "Numero de clientes a servir", example = "50")
        int numCustomers,

        @Schema(description = "Vehiculos disponibles en cada deposito", example = "4")
        int vehiclesPerDepot,

        @Schema(description = "Capacidad de carga de cada vehiculo. Las instancias del banco la "
                + "comparten entre depositos; el valor por deposito esta en el detalle",
                example = "80")
        int vehicleCapacity,

        @Schema(description = "Duracion maxima de ruta, en las mismas unidades que las distancias. "
                + "Ausente significa sin limite: el fichero lo codifica con un 0, que no es una "
                + "duracion sino la ausencia del dato",
                example = "310")
        Double maxDuration,

        @Schema(description = "Suma de la demanda de todos los clientes", example = "777")
        int totalDemand,

        @Schema(description = "Fraccion de la capacidad total de la flota que consume la demanda. "
                + "Mide lo apretada que esta la instancia: cuanto mas cerca de 1, menos holgura "
                + "hay para repartir",
                example = "0.6070")
        double loadRatio
) {
}
