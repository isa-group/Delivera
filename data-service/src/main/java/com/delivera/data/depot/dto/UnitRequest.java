package com.delivera.data.depot.dto;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import com.delivera.data.depot.model.UnitType;
import com.delivera.data.dto.common.OrderPriority;

@Getter
@Setter
@AllArgsConstructor
public class UnitRequest {


        @NotBlank
        @Size(max = 255)
        private String name;

        @NotNull
        private UnitType type;

        @Size(max = 500)
        private String address;

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private BigDecimal latitude;

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private BigDecimal longitude;

        private OrderPriority defaultPriority;

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
