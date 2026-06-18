package com.delivera.fms.dto;

import java.util.List;

public record RoutingRequest(
        String problemId,
        List<DepotDto> depots,
        List<CustomerDto> customers,
        // Uncomment when we have vehicles in the request
        // List<VehicleDto> vehicles,
        double[][] distanceMatrix
) {
}
