package com.delivera.data.fms.service;


import java.util.UUID;

import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.RoutingRequest;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.TypeSolver;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId, TypeSolver solverType);

    RoutingRequest getRoutingRequestForCompany(UUID companyId, TypeSolver solverType, Boolean showAllDepots );

    DbscanResult clusters(UUID companyId,ClusterConfig config, TypeSolver solverType, Boolean showAllDepots);
}
