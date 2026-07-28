package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.activity.ActivityMetricsResponse;
import com.delivera.dto.admin.*;
import com.delivera.org.dto.CompanyAdminProjection.InnerCompanyAdminProjection;
import com.delivera.org.service.CompanyService;
import com.delivera.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Panel de administración global")
public class AdminController {

    private final AdminService adminService;
    private final CompanyService companyService;
    private final SecurityUtils securityUtils;


    @Operation(summary = "Lista de organizaciones con métricas")
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationSummary>> listOrganizations() {
        return ResponseEntity.ok(adminService.listOrganizations());
    }

    @Operation(summary = "Métricas globales de la plataforma")
    @GetMapping("/metrics")
    public ResponseEntity<GlobalMetrics> getGlobalMetrics() {
        return ResponseEntity.ok(adminService.getGlobalMetrics());
    }

    @Operation(summary = "Lista de empresas")
    @GetMapping("/companies")
    public ResponseEntity<List<CompanySummary>> listCompanies() {
        return ResponseEntity.ok(adminService.listCompanies());
    }

    @Operation(summary = "Lista de empresas")
    @GetMapping("/organizations/companies")
    public ResponseEntity<Map<UUID,InnerCompanyAdminProjection>> getCompanyAndOrgNames() {
        return ResponseEntity.ok(companyService.getCompanyAndOrgNames());
    }

    @Operation(summary = "Lista de pedidos")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderAdminSummary>> listOrders() {
        return ResponseEntity.ok(adminService.listOrders());
    }

    @Operation(summary = "Lista de usuarios")
    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @Operation(summary = "Lista de trabajadores")
    @GetMapping("/workers")
    public ResponseEntity<List<WorkerAdminSummary>> listWorkers() {
        return ResponseEntity.ok(adminService.listWorkers());
    }
/*TODO:P009-ADMIN
    @Operation(summary = "Métricas globales de actividad por período")
    @GetMapping("/activity")
    public ResponseEntity<ActivityMetricsResponse> getGlobalActivity(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(adminService.getGlobalActivityMetrics(period));
    }

    @Operation(summary = "Pedidos por día (global)")
    @GetMapping("/activity/orders-by-day")
    public ResponseEntity<List<OrdersByDayEntry>> getGlobalOrdersByDay(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(adminService.getGlobalOrdersByDay(period));
    }*/

    @Operation(summary = "Lista de unidades con coordenadas (global)")
    @GetMapping("/units")
    public ResponseEntity<List<UnitAdminSummary>> listUnits() {
        return ResponseEntity.ok(adminService.listUnits());
    }

    @Operation(summary = "Rutas activas (PENDING/IN_TRANSIT) con coordenadas de origen y destino")
    @GetMapping("/routes")
    public ResponseEntity<List<RouteAdminEntry>> getActiveRoutes() {
        return ResponseEntity.ok(adminService.getActiveRoutes());
    }

    @Operation(summary = "Ranking de empresas por pedidos")
    @GetMapping("/activity/company-ranking")
    public ResponseEntity<List<CompanyRankingEntry>> getCompanyRanking(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(adminService.getCompanyRanking(period));
    }

    @Operation(summary = "Eliminar organización en cascada")
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable UUID id) {
        adminService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar empresa en cascada")
    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        adminService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar pedido")
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        adminService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar usuario")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar trabajador")
    @DeleteMapping("/workers/{id}")
    public ResponseEntity<Void> deleteWorker(@PathVariable UUID id) {
        adminService.deleteWorker(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Limpiar base de datos (mantiene admin)")
    @PostMapping("/reset")
    public ResponseEntity<Void> resetDatabase() {
        adminService.resetDatabase(securityUtils.getCurrentEmail());
        return ResponseEntity.noContent().build();
    }
}
