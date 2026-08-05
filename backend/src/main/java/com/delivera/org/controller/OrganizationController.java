package com.delivera.org.controller;

import com.delivera.auth.service.AuthService;
import com.delivera.dto.common.AvailabilityCheckResponse;
import com.delivera.org.service.OrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/organizations")
@Tag(name = "Organizaciones", description = "Endpoints públicos de organizaciones")
public class OrganizationController {

    private final AuthService authService;
    private final OrganizationService organizationService;

    @Operation(summary = "Comprobar disponibilidad de handle de organización")
    @GetMapping("/check-handle")
    public ResponseEntity<AvailabilityCheckResponse> checkHandle(@RequestParam String handle) {
        return ResponseEntity.ok(new AvailabilityCheckResponse(authService.isHandleAvailable(handle)));
    }

    
    @Operation(summary = "Obtain organizations names")
    @GetMapping("/names")
    public ResponseEntity<Map<UUID,String>> getOrganizationsNames() {
        return ResponseEntity.ok(organizationService.getOrganizationsNames());
    }
}
