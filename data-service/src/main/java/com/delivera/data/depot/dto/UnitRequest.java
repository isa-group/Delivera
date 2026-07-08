package com.delivera.data.depot.dto;


import jakarta.validation.constraints.*;

import java.math.BigDecimal;

import com.delivera.data.depot.model.UnitType;
import com.delivera.data.dto.common.OrderPriority;

public record UnitRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        UnitType type,

        @Size(max = 500)
        String address,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal longitude,

        OrderPriority defaultPriority
) {
    @AssertTrue(message = "Latitude and longitude must both be provided or both be absent")
    public boolean isCoordinatesConsistent() {
        return (latitude == null) == (longitude == null);
    }

    @AssertTrue(message = "MISSING_UNIT_LOCATION")
    public boolean isLocationPresent() {
        boolean hasAddress = address != null && !address.isBlank();
        boolean hasCoords = latitude != null && longitude != null;
        return hasAddress || hasCoords;
    }
}
