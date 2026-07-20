package com.delivera.fms.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.fms.dto.RoutingResponse;
import com.delivera.fms.dto.TypeSolver;
import com.delivera.fms.service.FmsRoutingService;
import com.delivera.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
}
