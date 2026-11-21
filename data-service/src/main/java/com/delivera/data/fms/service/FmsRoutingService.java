package com.delivera.data.fms.service;


import java.util.Map;
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

    /** Analisis previo (sin resolver) de un subconjunto de la empresa con el solver indicado. */
    Map<String, Object> analyzeForCompany(
        UUID companyId,
        Set<UUID> customers,
        Set<UUID> depots,
        TypeSolver solverType
    );

    /** Analisis previo (sin resolver) de un subconjunto de una instancia benchmark. */
    Map<String, Object> analyzeForInstance(String name, Set<String> customers, Set<String> depots, TypeSolver solverType);



    RoutingResponse solverForCompany(
        UUID companyId, 
        Set<UUID> customers, 
        Set<UUID> depots ,
        TypeSolver solverType
    );

    InstanceCatalog getInstanceSummaries();

    
    RoutingRequest coreCreateRequest( 
        UUID companyId, 
        TypeSolver solverType,
        DeliveryWindow window, 
        Boolean showAllDepots,
        Boolean buildMatrix
    );
    
    RoutingRequest getRoutingRequestForCompany(
        UUID companyId, 
        TypeSolver solverType, 
        Boolean showAllDepots, 
        Boolean buildMatrix 
    );


    DbscanResult clustersForCompany(
        UUID companyId, 
        ClusterConfig config, 
        TypeSolver solverType, 
        Boolean showAllDepots
    );


    DbscanResult clusters(
        ClusterConfig config, 
        RoutingRequest request
    );

    RoutingRequest getRoutingRequestForCompany(
        UUID companyId, 
        Set<UUID> clients, 
        Set<UUID> depots,
        TypeSolver solverType
    );

    RoutingRequest getInstance(
        String name, 
        TypeSolver type, 
        boolean buildMatrix
    );

    DbscanResult clustersForInstance(
        String instanceId, 
        ClusterConfig config, 
        TypeSolver solverType
    );

    RoutingResponse solverForInstance(String name, Set<String> customers, Set<String> depots, TypeSolver solverType);
}
