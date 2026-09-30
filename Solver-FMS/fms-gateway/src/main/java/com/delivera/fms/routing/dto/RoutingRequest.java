package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

@Schema(description = "Request to solve a vehicle routing problem")
public record RoutingRequest(
        @Schema(description = "Unique identifier of the problem to solve", example = "PROBLEM-001")
        @NotBlank String problemId,

        @ArraySchema(
                arraySchema = @Schema(description = "List of available depots"),
                schema = @Schema(implementation = DepotDto.class))
        @NotEmpty @Valid List<DepotDto> depots,

        @ArraySchema(
                arraySchema = @Schema(description = "List of customers to serve"),
                schema = @Schema(implementation = CustomerDto.class))
        @NotEmpty @Valid List<CustomerDto> customers,

        @ArraySchema(
                arraySchema = @Schema(description = "List of available vehicles (optional)"),
                schema = @Schema(implementation = VehicleDto.class))
        @Valid List<VehicleDto> vehicles,

        @Schema(description = "Distance matrix between all nodes (depots + customers), " +
                "indexed by matrixIndex. Rows are the origin node and columns the destination node: " +
                "distanceMatrix[origin][destination]. It must be square, of size (depots + customers).")
        @NotNull double[][] distanceMatrix,

        @Schema(description = "Solver type to use")
        @NotNull TypeSolver solverType,

        @Schema(description = "Solver invocation parameters, by name. Those not sent " +
                "take their default value. Each solver publishes the ones it accepts, with their meaning, " +
                "range and default value, at GET /api/v1/fms/solvers/{type}: GREEDY accepts " +
                "none, RANDOM only 'seed', GENETIC fourteen (populationSize, " +
                "maxEvaluations, minGenerations, crossoverProbability, intraDepotMutationProbability, " +
                "interDepotMutationProbability, elitismCount, tournamentSize, localSearchFrequency, " +
                "interDepotFrequency, restartStagnantGenerations, maxRestarts, heuristicSeedRatio and seed) " +
                "and ANNEALING twelve (timeLimitMs, maxLevels, calibrationQuantile, initialAcceptanceRate, " +
                "finalAcceptanceRate, coolingRate, movesPerTemperatureFactor, interDepotMoveProbability, " +
                "depotCandidateRatio, localSearchFrequency, rebalanceFrequency and seed). " +
                "'seed' fixes the random generator and makes the run reproducible; if not sent, " +
                "the engine draws one and returns it in the response's 'seed' field. " +
                "An undeclared parameter is ignored with a warning; one out of range rejects the request",
                example = "{\"populationSize\": 200, \"maxEvaluations\": 120000, \"seed\": 1234}")
        Map<String, Object> parameters
) {

    // Copia con los parametros ya resueltos contra los metadatos del solver.
    public RoutingRequest withParameters(Map<String, Object> resolved) {
        return new RoutingRequest(problemId, depots, customers, vehicles, distanceMatrix,
                solverType, resolved);
    }
}
