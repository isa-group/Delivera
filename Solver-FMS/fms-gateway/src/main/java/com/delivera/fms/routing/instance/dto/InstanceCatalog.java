package com.delivera.fms.routing.instance.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "InstanceCatalog",
        description = "Catalogo de instancias estandar disponibles en el gateway. Se deriva del "
                + "contenido del directorio de instancias, por lo que crece con solo anadir ficheros")
public record InstanceCatalog(

        @Schema(description = "Numero de instancias devueltas", example = "33")
        int total,

        @ArraySchema(
                arraySchema = @Schema(description = "Instancias del banco, ordenadas por nombre"),
                schema = @Schema(implementation = InstanceSummary.class))
        List<InstanceSummary> instances
) {

    public static InstanceCatalog of(List<InstanceSummary> instances) {
        return new InstanceCatalog(instances.size(), instances);
    }
}
