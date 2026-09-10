package com.delivera.data.fms.dto;

import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class SolveClusterRequest {

    private  Set<UUID> customers;

    private  Set<UUID> depots;

}
