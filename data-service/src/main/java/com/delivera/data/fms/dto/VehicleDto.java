package com.delivera.data.fms.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VehicleDto {
        @NotBlank
        private UUID id;

        @NotNull
        @Min(1)
        private Integer capacity;

        @NotBlank
        private UUID startDepotId;

}
