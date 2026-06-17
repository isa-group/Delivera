package com.delivera.fms.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoutingRequest(
        @NotBlank
        String problemId,

        @NotEmpty
        @Valid
        List<DepotDto> depots,

        @NotEmpty
        @Valid
        List<CustomerDto> customers,

        //Uncomment when we have vehicles in the request
        // @NotEmpty
        // @Valid
        // List<VehicleDto> vehicles,

        @NotNull
        double[][] distanceMatrix
) {
}