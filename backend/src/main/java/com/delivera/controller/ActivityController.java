package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.activity.ActivityMetricsResponse;
import com.delivera.dto.activity.OrdersByDayEntry;
import com.delivera.dto.activity.UnitRankingEntry;

import com.delivera.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/activity")
@Tag(name = "Actividad", description = "Panel de actividad de la empresa")
public class ActivityController {

    private final ActivityService activityService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Métricas de actividad por período")
    @GetMapping("/metrics")
    public ResponseEntity<ActivityMetricsResponse> getMetrics(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(activityService.getMetrics(securityUtils.getCurrentCompanyId(), period));
    }

    @Operation(summary = "Pedidos por día en el período")
    @GetMapping("/orders-by-day")
    public ResponseEntity<List<OrdersByDayEntry>> getOrdersByDay(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(activityService.getOrdersByDay(securityUtils.getCurrentCompanyId(), period));
    }

    @Operation(summary = "Ranking de unidades por volumen de pedidos")
    @GetMapping("/unit-ranking")
    public ResponseEntity<List<UnitRankingEntry>> getUnitRanking(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(activityService.getUnitRanking(securityUtils.getCurrentCompanyId(), period));
    }
}
