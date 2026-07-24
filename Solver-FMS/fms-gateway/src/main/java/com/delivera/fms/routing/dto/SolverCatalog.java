package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Catalogo de solvers disponibles en el gateway. Crece automaticamente " +
        "a medida que se registran nuevos motores en la configuracion.")
public record SolverCatalog(

        @Schema(description = "Numero de solvers devueltos", example = "3")
        int total,

        @ArraySchema(
                arraySchema = @Schema(description = "Solvers registrados, ordenados por el peso de configuracion"),
                schema = @Schema(implementation = SolverInfo.class))
        List<SolverInfo> solvers
) {

    public static SolverCatalog of(List<SolverInfo> solvers) {
        return new SolverCatalog(solvers.size(), solvers);
    }
}
