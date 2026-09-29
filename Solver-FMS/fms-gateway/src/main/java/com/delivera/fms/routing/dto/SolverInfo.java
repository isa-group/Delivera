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
@Schema(description = "Metadata of a solver registered in the gateway")
public record SolverInfo(

        @Schema(description = "Solver identifier, the same value sent in solverType",
                example = "GENETIC")
        TypeSolver type,

        @Schema(description = "Human-readable solver name", example = "Genetic Algorithm")
        String name,

        @Schema(description = "Functional description of the algorithm",
                example = "Evolutionary metaheuristic with local search for the MD-CVRP")
        String description,

        @Schema(description = "Algorithmic family the solver belongs to",
                example = "Metaheuristic")
        String strategy,

        @Schema(description = "Technology the engine is implemented with. Informative: the " +
                "gateway only depends on the HTTP contract",
                example = "Java 22 / Spring Boot + jMetal 6.6")
        String technology,

        @Schema(description = "Solver version. Pinning it is what makes a benchmark result citable",
                example = "1.0.0")
        String version,

        @Schema(description = "Whether two runs on the same input produce the same solution",
                example = "false")
        boolean deterministic,

        @ArraySchema(
                arraySchema = @Schema(description = "Invocation parameters with their default values"),
                schema = @Schema(implementation = SolverParameter.class))
        List<SolverParameter> parameters,

        @Schema(description = "Engine status. Only checked when requested with includeStatus=true; " +
                "otherwise it is UNKNOWN")
        SolverStatus status
) {

    /** Copia esta informacion sustituyendo el estado por el recien comprobado. */
    public SolverInfo withStatus(SolverStatus newStatus) {
        return new SolverInfo(type, name, description, strategy, technology, version,
                deterministic, parameters, newStatus);
    }
}
