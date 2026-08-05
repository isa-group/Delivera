package com.delivera.data.depot.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.delivera.data.depot.dto.AssignRequest;
import com.delivera.data.depot.dto.B2BUnitResponse;
import com.delivera.data.depot.dto.UnitDetailResponse;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.dto.UnitResponse;
import com.delivera.data.depot.service.UnitService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/units")
@Tag(name = "Unidades", description = "Gestión de unidades operativas")
public class UnitController {

    private final UnitService unitService;

    
    @Operation(summary = "Listar unidades de la empresa")
    @GetMapping
    public ResponseEntity<List<UnitResponse>> list() {
        return ResponseEntity.ok(unitService.getByCompany());
    }

    @Operation(summary = "Listar unidades de otras empresas de la misma organización (B2B)")
    @GetMapping("/external/company/{companyId}")
    public ResponseEntity<List<B2BUnitResponse>> listExternal(@Valid @PathVariable UUID companyId ) {
        return ResponseEntity.ok(unitService.getExternalUnits(companyId));
    }
    /* TODO: CREO QUE NO SE UTILIZA
    @Operation(summary = "Listar empresas de la misma organización (B2B)")
    @GetMapping("/external-companies")
    public ResponseEntity<List<CompanySummary>> listExternalCompanies() {
        return ResponseEntity.ok(unitService.getExternalCompanies());
    }
    */

    @GetMapping("/names")
    public ResponseEntity<Map<UUID,String>> getByOrganization(
        @RequestParam(required = true, name = "companyId") UUID companyId 
    ) {
        return ResponseEntity.ok().body(
            unitService.getByCompanyId(companyId)
        );
    }

    @Operation(summary = "Crear unidad operativa")
    @PostMapping
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(request));
    }

    @Operation(summary = "Detalle de una unidad operativa")
    @GetMapping("/{id}")
    public ResponseEntity<UnitDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(unitService.getDetail(id));
    }

    
    @Operation(summary = "Asignar trabajador a una unidad")
    @PostMapping("/{id}/workers")
    public ResponseEntity<Set<UUID>> assignWorker(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(unitService.assignWorker(id, request));
    }

    
    @Operation(summary = "Desasignar trabajador de una unidad")
    @DeleteMapping("/{id}/workers/{workerId}")
    public ResponseEntity<Set<UUID>> unassignWorker(@PathVariable UUID id, @PathVariable UUID workerId) {
        return ResponseEntity.ok(unitService.unassignWorker(id, workerId));
    }

    @Operation(summary = "Obtener los trabajadores de una unidad")
    @GetMapping("/{id}/workers")
    public ResponseEntity<Set<UUID>> getWorkers(@PathVariable UUID id) {
        return ResponseEntity.ok(unitService.getWorkersIdByUnit(id));
    }
    

    @Operation(summary = "Editar unidad operativa")
    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.ok(unitService.update(id, request));
    }

    @Operation(summary = "Eliminar unidad operativa")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        unitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
