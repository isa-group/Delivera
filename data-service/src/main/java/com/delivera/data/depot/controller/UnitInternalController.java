package com.delivera.data.depot.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.delivera.data.depot.dto.AssignRequest;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.service.UnitService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/units")
@Tag(name = "Internal-Units", description = "Manage internal operations over units")
public class UnitInternalController {

    private final UnitService unitService;

   
    
    //@Profile(value = {"dev"})
    @Operation(summary = "Crear unidad operativa")
    @PostMapping("/seed/organizations/{orgId}/companies/{companyId}")
    public ResponseEntity<UUID> createSeed(
        @Valid @PathVariable(name = "orgId") UUID orgId,
        @Valid @PathVariable(name = "companyId") UUID companyId,
        @Valid @RequestBody UnitRequest request
    ) {
        log.info("ENTRO AQUÍ");
        var unit = unitService.createSeed(request, orgId, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(unit.id());
    }

    //@Profile(value = {"dev"})
    @Operation(summary = "Crear unidad operativa")
    @PostMapping("{unitId}/seed/assign")
    public ResponseEntity<Void> createAssign(
        @Valid @PathVariable(name = "unitId") UUID unitId,
        @Valid @RequestBody  AssignRequest request
    ) {
        unitService.assignWorkerSeed(unitId,request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    
    //@Profile(value = {"dev"})
    @Operation(summary = "Crear unidad operativa")
    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<Void> createAssign(
        @Valid @PathVariable(name = "companyId") UUID companyId
    ) {
        unitService.deleteByCompanyId(companyId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}
