package com.delivera.data.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.data.admin.service.AdminService;
import com.delivera.data.order.dto.OrderAdminSummary;

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

}
