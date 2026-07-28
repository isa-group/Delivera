package com.delivera.data.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.data.activity.dto.ActivityMetricsResponse;
import com.delivera.data.activity.dto.OrdersByDayEntry;
import com.delivera.data.admin.service.AdminService;
import com.delivera.data.common.dto.IdCountProjection;
import com.delivera.data.common.dto.IdNameProjection;
import com.delivera.data.depot.dto.UnitAdminSummary;
import com.delivera.data.order.dto.OrderAdminSummary;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/organizations/orders")
    public ResponseEntity<Map<UUID,Long>> countByOrganization() {
        return ResponseEntity.ok().body(
            adminService.countByOrganization()
        );
    }

    @GetMapping("/companies/orders")
    public ResponseEntity<Map<UUID,Long>> countByCompany() {
        return ResponseEntity.ok().body(
            adminService.countByCompany()
        );
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderAdminSummary>> getOrdersSummary() {
        return ResponseEntity.ok().body(
            adminService.getOrdersSummary()
        );
    }

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
    }

    @Operation(summary = "Pedidos este mes")
    @GetMapping("/activity/orders")
    public ResponseEntity<Long> getOrdersThisMonth() {
        return ResponseEntity.ok(adminService.getOrdersThisMonth());
    }

    @Operation(summary = "Lista de unidades con coordenadas (global)")
    @GetMapping("/units")
    public ResponseEntity<List<UnitAdminSummary>> listUnits() {
        return ResponseEntity.ok(adminService.listUnits());
    }

    @Operation(summary = "Ranking de empresas por pedidos")
    @GetMapping("/activity/company-ranking")
    public ResponseEntity<List<IdCountProjection>> getCompanyRanking(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(adminService.getCompanyRanking(period));
    }


}
