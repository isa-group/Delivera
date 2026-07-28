package com.delivera.vehicle.dto;

import java.time.Instant;
import java.util.UUID;


public record VehicleResponse(
        UUID id,
        String plate,
        Integer capacity,
        UUID depotId,
        String depotName,
        Instant createdAt
) {
    
}
