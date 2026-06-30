package com.delivera.dto.vehicle;

import com.delivera.model.Vehicle;

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
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getCapacity(),
                vehicle.getDepot().getId(),
                vehicle.getDepot().getName(),
                vehicle.getCreatedAt()
        );
    }
}
