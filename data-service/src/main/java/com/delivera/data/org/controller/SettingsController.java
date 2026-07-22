package com.delivera.data.org.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.service.SettingsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings")
public class SettingsController {

    private final SecurityUtils securityUtils;

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<CompanySettingsDTO> get() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(
            settingsService.get(companyId).dto()
        );
    }


    @PostMapping
    public ResponseEntity<CompanySettingsDTO> create(
        @RequestBody CompanySettingsDTO request
    ) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        request.setCompanyId(companyId);
        return ResponseEntity.status(201)
        .body(
            settingsService.create(request).dto()
        );
    }

    @PutMapping
    public ResponseEntity<Void> update(
        @RequestBody CompanySettingsDTO request
    ) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        request.setCompanyId(companyId);
        settingsService.update(request);
        return ResponseEntity.status(204).build();
    }



}
