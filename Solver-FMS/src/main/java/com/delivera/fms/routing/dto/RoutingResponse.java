package com.delivera.fms.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoutingResponse(
        @NotBlank
        String problemId,

        @NotBlank
        String status,

        @NotBlank
        TypeSolver solverUsed,

        @NotNull
        Long computationTimeMs,

        @NotEmpty
        @Valid
        List<RouteDto> routes
) {
}