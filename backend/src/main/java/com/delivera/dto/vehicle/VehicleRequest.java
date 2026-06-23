package com.delivera.dto.vehicle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VehicleRequest(
        @NotBlank
        @Size(max = 20)
        String plate,

        @NotNull
        @Min(1)
        Integer capacity,

        @NotNull
        UUID depotId
) {
}
