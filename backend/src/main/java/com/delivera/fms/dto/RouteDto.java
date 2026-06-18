package com.delivera.fms.dto;

import java.util.List;

public record RouteDto(
        String vehicleId,
        String depotId,
        List<String> stops,
        Double totalDistance,
        Integer totalLoad
) {
}
