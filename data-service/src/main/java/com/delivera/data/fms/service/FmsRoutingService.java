package com.delivera.data.fms.service;


import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DeliveryWindow;
import com.delivera.data.fms.dto.InstanceCatalog;
import com.delivera.data.fms.dto.RoutingRequest;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.TypeSolver;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId, TypeSolver solverType);


    RoutingResponse solverForCompany(UUID companyId, Set<UUID> customers, Set<UUID> depots ,TypeSolver solverType);

    InstanceCatalog getInstanceSummaries();

    RoutingRequest getInstance(String name);
    
    RoutingRequest coreCreateRequest( 
        UUID companyId, 
        TypeSolver solverType,
        DeliveryWindow window, 
        Boolean showAllDepots
    );

    RoutingRequest getRoutingRequestForCompany(UUID companyId, TypeSolver solverType, Boolean showAllDepots );

    DbscanResult clusters(UUID companyId,ClusterConfig config, TypeSolver solverType, Boolean showAllDepots);
}
