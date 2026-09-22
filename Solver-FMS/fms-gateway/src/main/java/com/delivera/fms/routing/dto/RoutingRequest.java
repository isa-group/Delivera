package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

@Schema(description = "Solicitud de resolucion de un problema de ruteo de vehiculos")
public record RoutingRequest(
        @Schema(description = "Identificador unico del problema a resolver", example = "PROBLEM-001")
        @NotBlank String problemId,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de depositos disponibles"),
                schema = @Schema(implementation = DepotDto.class))
        @NotEmpty @Valid List<DepotDto> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de clientes a atender"),
                schema = @Schema(implementation = CustomerDto.class))
        @NotEmpty @Valid List<CustomerDto> customers,

        @ArraySchema(
                arraySchema = @Schema(description = "Lista de vehiculos disponibles (opcional)"),
                schema = @Schema(implementation = VehicleDto.class))
        @Valid List<VehicleDto> vehicles,

        @Schema(description = "Matriz de distancias entre todos los nodos (depositos + clientes), " +
                "indexada por matrixIndex. Las filas representan el nodo de origen y las columnas el nodo de destino: " +
                "distanceMatrix[origen][destino]. Debe ser cuadrada de tamano (depots + customers).")
        @NotNull double[][] distanceMatrix,

        @Schema(description = "Tipo de solver a utilizar para la resolucion")
        @NotNull TypeSolver solverType,

        @Schema(description = "Parametros de invocacion del solver, por nombre. Los que no se envien " +
                "toman su valor por defecto. Cada solver publica los que admite, con su significado, " +
                "rango y valor por defecto, en GET /api/v1/fms/solvers/{type}: GREEDY no admite " +
                "ninguno, RANDOM admite solo 'seed' y GENETIC admite catorce (populationSize, " +
                "maxEvaluations, minGenerations, crossoverProbability, intraDepotMutationProbability, " +
                "interDepotMutationProbability, elitismCount, tournamentSize, localSearchFrequency, " +
                "interDepotFrequency, restartStagnantGenerations, maxRestarts, heuristicSeedRatio y seed). " +
                "'seed' fija el generador aleatorio y hace la ejecucion reproducible; si no se envia, " +
                "el motor sortea una y la devuelve en el campo 'seed' de la respuesta. " +
                "Un parametro no declarado se ignora con un aviso; uno fuera de rango rechaza la peticion",
                example = "{\"populationSize\": 200, \"maxEvaluations\": 120000, \"seed\": 1234}")
        Map<String, Object> parameters
) {

    // Copia con los parametros ya resueltos contra los metadatos del solver.
    public RoutingRequest withParameters(Map<String, Object> resolved) {
        return new RoutingRequest(problemId, depots, customers, vehicles, distanceMatrix,
                solverType, resolved);
    }
}
