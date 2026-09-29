package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Catalogue of solvers available in the gateway. It grows automatically " +
        "as new engines are registered in the configuration.")
public record SolverCatalog(

        @Schema(description = "Number of solvers returned", example = "4")
        int total,

        @ArraySchema(
                arraySchema = @Schema(description = "Registered solvers, sorted by their configured order"),
                schema = @Schema(implementation = SolverInfo.class))
        List<SolverInfo> solvers
) {

    public static SolverCatalog of(List<SolverInfo> solvers) {
        return new SolverCatalog(solvers.size(), solvers);
    }
}
