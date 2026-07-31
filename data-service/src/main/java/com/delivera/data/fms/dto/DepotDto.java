package com.delivera.data.fms.dto;

public record DepotDto(
        String id,
        Double lat,
        Double lng,
        Integer matrixIndex
) {
}
