package com.delivera.data.fms.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.service.FmsRoutingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/fms/routing")
public class FmsRoutingController {

    private final FmsRoutingService fmsRoutingService;
    private final SecurityUtils securityUtils;

    public FmsRoutingController(FmsRoutingService fmsRoutingService,
                                 SecurityUtils securityUtils) {
        this.fmsRoutingService = fmsRoutingService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        RoutingResponse response = fmsRoutingService.solveForCompany(companyId);
        return ResponseEntity.ok(response);
    }
}
