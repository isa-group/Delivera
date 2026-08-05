package com.delivera.data.admin.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.data.admin.service.AdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class AdminInternalController {

    private final AdminService adminService;

    @DeleteMapping("/data-service/{companyId}")
    public ResponseEntity<Void> deleteAllDataExcept(@PathVariable @Valid UUID companyId) {
        adminService.deleteAllDataExcept(companyId);
        return ResponseEntity.status(204).build();
    }

}
