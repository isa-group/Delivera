package com.delivera.data.depot.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.delivera.data.depot.model.OperationalUnit;



public record UnitResponse(
        UUID id,
        String name,
        String type,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        String defaultPriority
        ) {

    public static UnitResponse from(OperationalUnit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getType().name(),
                unit.getAddress(),
                unit.getLatitude(),
                unit.getLongitude(),
                unit.getCreatedAt(),
                unit.getDefaultPriority() != null ? unit.getDefaultPriority().name() : null
                );
    }
}
