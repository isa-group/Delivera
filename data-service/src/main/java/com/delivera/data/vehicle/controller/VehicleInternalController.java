package com.delivera.data.vehicle.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.delivera.data.vehicle.dto.VehicleRequest;
import com.delivera.data.vehicle.dto.VehicleResponse;
import com.delivera.data.vehicle.service.VehicleService;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/vehicles")
@Tag(name = "Vehículos", description = "Gestión de vehículos")
public class VehicleInternalController {

    private final VehicleService vehicleService;

    
    @Profile("dev")
    @Operation(summary = "Crear vehículo")
    @PostMapping("/seed/companies/{companyId}")
    public ResponseEntity<Void> createSeed(
        @Valid @RequestBody VehicleRequest request,
        @Valid @PathVariable(name = "companyId") UUID companyId
    ) {
        vehicleService.createSeed(companyId,request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @Operation(summary = "Crear vehículo")
    @GetMapping("/companies/{companyId}")
    public ResponseEntity<List<VehicleResponse>> getByCompany(
        @Valid @PathVariable(name = "companyId") UUID companyId
    ) {
        
        return ResponseEntity.status(HttpStatus.OK)
        .body(vehicleService.getByCompany(companyId));
    }
}
