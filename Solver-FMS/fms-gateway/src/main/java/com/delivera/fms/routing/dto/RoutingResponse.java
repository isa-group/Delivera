package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response with the solution of the routing problem")
public record RoutingResponse(
        @Schema(description = "Identifier of the solved problem", example = "p01")
        String problemId,

        @Schema(description = "Solving status (COMPLETED / FAILURE)", example = "COMPLETED")
        String status,

        @Schema(description = "Solver type used",
                allowableValues = {"RANDOM", "GREEDY", "GENETIC", "ANNEALING"}, example = "GREEDY")
        String solverUsed,

        @Schema(description = "Total cost of the solution (sum of the distances of all routes)", example = "775.2683743807665")
        Double totalCost,

        @Schema(description = "Computation time in milliseconds", example = "6")
        Long computationTimeMs,

        @Schema(description = "Seed the solver ran with. Sending it back in " +
                "'parameters' reproduces exactly this solution. Absent in deterministic " +
                "solvers, which do not depend on chance",
                example = "1234", nullable = true)
        Long seed,

        @ArraySchema(
                arraySchema = @Schema(description = "List of routes that make up the solution"),
                schema = @Schema(implementation = RouteDto.class))
        List<RouteDto> routes,

        @ArraySchema(
                arraySchema = @Schema(description = "Anytime curve: instants, since start-up, at " +
                        "which the best feasible solution improved. It tells what cost the engine would " +
                        "have given with a smaller budget without running it again. Only engines that " +
                        "search incrementally with a time budget return it (ANNEALING); " +
                        "the rest leave it null", nullable = true),
                schema = @Schema(implementation = TracePointDto.class))
        List<TracePointDto> trace
) {
}
