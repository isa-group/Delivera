package com.delivera.data.fms.dto.solver.request;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class SolveInstanceClusterRequest {

    private  Set<String> customers;

    private  Set<String> depots;

}
