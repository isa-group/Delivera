package com.delivera.controller;

import com.delivera.dto.vehicle.VehicleRequest;
import com.delivera.dto.vehicle.VehicleResponse;
import com.delivera.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/vehicles")
@Tag(name = "Vehículos", description = "Gestión de vehículos")
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "Listar vehículos de la empresa")
    @GetMapping
    public ResponseEntity<List<VehicleResponse>> list() {
        return ResponseEntity.ok(vehicleService.getByCompany());
    }

    @Operation(summary = "Crear vehículo")
    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(request));
    }

    @Operation(summary = "Detalle de un vehículo")
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleService.getDetail(id));
    }

    @Operation(summary = "Editar vehículo")
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

    @Operation(summary = "Eliminar vehículo")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
