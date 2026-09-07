package com.delivera.data.fms.dto;

import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ClusterConfig {

    @Builder.Default
    @Min(value = 0)
    private double maxRadiusKm = 250;

    @Builder.Default
    @Min(value = 0)
    private int maxDepotRadiusKm = 100;

    @Builder.Default
    @Min(value = 1)
    private int minClusterSize = 3;

    @Builder.Default
    @Min(value = 1)
    private int maxClusterSize = 100;


    @Builder.Default
    @Min(value = 0)
    private double noiseClusterMaxDistanceKm = 250;

    @Builder.Default
    @Min(value = 0)
    private double noiseMaxDistanceKm = 250;




}
