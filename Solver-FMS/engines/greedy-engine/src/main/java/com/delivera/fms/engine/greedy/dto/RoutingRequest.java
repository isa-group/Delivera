package com.delivera.fms.engine.greedy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoutingRequest(
        @NotBlank String problemId,
        @NotEmpty @Valid List<DepotDto> depots,
        @NotEmpty @Valid List<CustomerDto> customers,
        @Valid List<VehicleDto> vehicles,
        @NotNull double[][] distanceMatrix
) {
}
