package com.delivera.fms.dto;

public record DepotDto(
        String id,
        Double lat,
        Double lng,
        Integer matrixIndex
) {
}
