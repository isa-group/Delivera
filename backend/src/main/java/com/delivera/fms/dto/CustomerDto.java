package com.delivera.fms.dto;

public record CustomerDto(
        String id,
        Integer demand,
        Double lat,
        Double lng,
        Integer matrixIndex
) {
}
