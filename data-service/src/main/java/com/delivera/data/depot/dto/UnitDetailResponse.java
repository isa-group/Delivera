package com.delivera.data.depot.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.delivera.data.depot.model.OperationalUnit;



public record UnitDetailResponse(
        UUID id,
        String name,
        String type,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        String defaultPriority
    ) {

    public static UnitDetailResponse from(OperationalUnit unit) {
        return new UnitDetailResponse(
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
