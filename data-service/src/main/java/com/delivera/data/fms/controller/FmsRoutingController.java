package com.delivera.data.fms.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.RoutingRequest;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.SolveClusterRequest;
import com.delivera.data.fms.dto.TypeSolver;
import com.delivera.data.fms.service.FmsRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/fms/routing")
@Tag(name = "FMS Routing", description = "Optimizacion de rutas de flota")
@SecurityRequirement(name = "bearerAuth")
public class FmsRoutingController {

    private final FmsRoutingService fmsRoutingService;
    private final SecurityUtils securityUtils;

    public FmsRoutingController(FmsRoutingService fmsRoutingService,
                                 SecurityUtils securityUtils) {
        this.fmsRoutingService = fmsRoutingService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "Resolver rutas de la empresa",
            description = "Resuelve el problema de ruteo de vehiculos para la empresa autenticada. " +
                    "Obtiene depositos, ordenes pendientes y vehiculos de la base de datos, " +
                    "construye el problema y lo envia al FMS Routing Service para su optimizacion.")
    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(
            @Parameter(description = "Tipo de solver a utilizar (RANDOM o GREEDY)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        RoutingResponse response = fmsRoutingService.solveForCompany(companyId, solverType);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Get data that will be used to run MD-CVRP solvers ",
            description = "Obtain the related company's data that is used in the MD-CVRP")
    @GetMapping("/data")
    public ResponseEntity<RoutingRequest> data(
            @Parameter(description = "Tipo de solver a utilizar (RANDOM o GREEDY)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        RoutingRequest response = fmsRoutingService.getRoutingRequestForCompany(companyId, solverType, true);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Get data that will be used to run MD-CVRP solvers ",
            description = "Obtain the related company's data that is used in the MD-CVRP")
    @PostMapping("/cluster")
    public ResponseEntity<DbscanResult> cluster(
            @Parameter(description = "Tipo de solver a utilizar (RANDOM o GREEDY)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType,
            @RequestBody ClusterConfig config) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        DbscanResult response = fmsRoutingService.clusters(companyId,config, solverType, false);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resolver rutas de la empresa",
    description = "Resuelve el problema de ruteo de vehiculos para la empresa autenticada. " +
            "Obtiene depositos, ordenes pendientes y vehiculos de la base de datos, " +
            "construye el problema y lo envia al FMS Routing Service para su optimizacion.")
    @PostMapping("/solve/cluster")
    public ResponseEntity<RoutingResponse> solveCluster(
        @Parameter(description = "Tipo de solver a utilizar (RANDOM o GREEDY)")
        @RequestParam(defaultValue = "GREEDY") TypeSolver solverType,
        @RequestBody  SolveClusterRequest request
    ) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        RoutingResponse response = fmsRoutingService.solverForCompany(
            companyId, 
            request.getCustomers(),
            request.getDepots(),
            solverType
        );
        return ResponseEntity.ok(response);
    }


}
