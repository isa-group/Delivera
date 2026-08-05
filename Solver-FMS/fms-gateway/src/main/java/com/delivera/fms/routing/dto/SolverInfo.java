package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Metainformacion completa de un solver registrado.
 *
 * Es el contrato que convierte a la pasarela en un paraguas sobre solvers de
 * tecnologias distintas: describe que hace el algoritmo y con que parametros se
 * invoca, sin decir nada de como esta implementado. Un motor escrito en Python o
 * un solver exacto comercial se integran declarando este mismo descriptor.
 */
@Schema(description = "Metainformacion de un solver registrado en el gateway")
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

        @Schema(description = "Tecnologia con la que esta implementado el motor. Informativo: la " +
                "pasarela solo depende del contrato HTTP",
                example = "Java 22 / Spring Boot + jMetal 6.6")
        String technology,

        @Schema(description = "Version del solver. Fijarla es lo que hace citable un resultado de benchmark",
                example = "1.0.0")
        String version,

        @Schema(description = "Indica si dos ejecuciones sobre la misma entrada producen la misma solucion",
                example = "false")
        boolean deterministic,

        @ArraySchema(
                arraySchema = @Schema(description = "Parametros de invocacion con sus valores por defecto"),
                schema = @Schema(implementation = SolverParameter.class))
        List<SolverParameter> parameters,

        @Schema(description = "Estado del motor. Solo se comprueba si se solicita con includeStatus=true, " +
                "en caso contrario vale UNKNOWN")
        SolverStatus status
) {

    /** Copia esta informacion sustituyendo el estado por el recien comprobado. */
    public SolverInfo withStatus(SolverStatus newStatus) {
        return new SolverInfo(type, name, description, strategy, technology, version,
                deterministic, parameters, newStatus);
    }
}
