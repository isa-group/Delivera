package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informacion publica de un solver registrado en el gateway")
public record SolverInfo(

        @Schema(description = "Identificador del solver, el mismo valor que se envia en solverType",
                example = "GENETIC")
        TypeSolver type,

        @Schema(description = "Nombre legible del solver", example = "Algoritmo Genetico")
        String name,

        @Schema(description = "Descripcion funcional del algoritmo",
                example = "Metaheuristica evolutiva con busqueda local para MD-CVRP")
        String description,

        @Schema(description = "Familia algoritmica a la que pertenece el solver",
                example = "Metaheuristica")
        String strategy,

        @Schema(description = "Indica si dos ejecuciones sobre la misma entrada producen la misma solucion",
                example = "false")
        boolean deterministic,

        @Schema(description = "Estado del motor. Solo se comprueba si se solicita con includeStatus=true, " +
                "en caso contrario vale UNKNOWN")
        SolverStatus status
) {

    /** Copia esta informacion sustituyendo el estado por el recien comprobado. */
    public SolverInfo withStatus(SolverStatus newStatus) {
        return new SolverInfo(type, name, description, strategy, deterministic, newStatus);
    }
}
