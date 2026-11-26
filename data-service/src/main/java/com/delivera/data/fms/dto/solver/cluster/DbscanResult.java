package com.delivera.data.fms.dto.solver.cluster;

import java.util.List;

import com.delivera.data.fms.dto.solver.data.CustomerDto;
import com.delivera.data.fms.dto.solver.data.DepotDto;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DbscanResult {

    private Long computationTimeMs;

    private ClusterConfig config;

    private List<Cluster> clusters;

    @JsonIgnore
    private List<CustomerDto> noise;

    private List<DepotDto> depots;

}
