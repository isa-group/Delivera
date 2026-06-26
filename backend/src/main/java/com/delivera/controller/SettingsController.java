package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.settings.*;
import com.delivera.service.SettingsService;
import com.delivera.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/settings")
@Tag(name = "Configuración", description = "Gestión de organización y empresa")
public class SettingsController {

    private final SettingsService settingsService;
    private final SubscriptionService subscriptionService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Obtener configuración de organización y empresa")
    @GetMapping
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @Operation(summary = "Actualizar organización")
    @PutMapping("/organization")
    public ResponseEntity<SettingsResponse> updateOrg(@Valid @RequestBody OrgUpdateRequest req) {
        return ResponseEntity.ok(settingsService.updateOrg(req));
    }

    @Operation(summary = "Actualizar empresa")
    @PutMapping("/company")
    public ResponseEntity<SettingsResponse> updateCompany(@Valid @RequestBody CompanyUpdateRequest req) {
        return ResponseEntity.ok(settingsService.updateCompany(req));
    }

    @Operation(summary = "Listar empresas del usuario en la misma organización")
    @GetMapping("/companies")
    public ResponseEntity<List<CompanySummary>> getMyCompanies() {
        return ResponseEntity.ok(settingsService.getMyCompanies());
    }

    @Operation(summary = "Crear nueva empresa en la misma organización")
    @PostMapping("/companies")
    public ResponseEntity<CompanySummary> createCompany(@Valid @RequestBody CompanyCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settingsService.createCompany(req));
    }

    @Operation(summary = "Obtener uso del plan de suscripción")
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionUsageResponse> getSubscription() {
        return ResponseEntity.ok(subscriptionService.getUsage(securityUtils.getCurrentCompanyId()));
    }

    @Operation(summary = "Cambiar plan de suscripción")
    @PatchMapping("/subscription/plan")
    public ResponseEntity<SubscriptionUsageResponse> changePlan(@Valid @RequestBody ChangePlanRequest req) {
        return ResponseEntity.ok(subscriptionService.changePlan(securityUtils.getCurrentCompanyId(), req.planCode(), req.force()));
    }

    @Operation(summary = "Actualizar logo de empresa")
    @PutMapping("/company/logo")
    public ResponseEntity<CompanySummary> updateCompanyLogo(@RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(settingsService.updateCompanyLogo(body.get("data")));
    }

    @Operation(summary = "Eliminar empresa de la organización")
    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> deleteCompany(
            @PathVariable java.util.UUID id,
            @RequestParam(defaultValue = "false") boolean force) {
        settingsService.deleteCompany(id, force);
        return ResponseEntity.noContent().build();
    }
}
