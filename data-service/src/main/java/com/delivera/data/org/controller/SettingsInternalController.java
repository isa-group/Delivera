package com.delivera.data.org.controller;


import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.service.SettingsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/settings")
public class SettingsInternalController {


    private final SettingsService settingsService;

   

    @PostMapping("/seed")
    public ResponseEntity<Void> createSeed(
        @RequestBody CompanySettingsDTO request
    ) {
        settingsService.create(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<CompanySettingsDTO> create(
        @RequestBody CompanySettingsDTO request
    ) {
        return ResponseEntity.status(201)
        .body(
            settingsService.create(request).dto()
        );
    }


    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> deleteCompany(
        @RequestBody Boolean confirmation,
        @PathVariable(name = "companyId") UUID companyId
    ) {
        settingsService.deleteCompany(companyId,confirmation);
        return ResponseEntity.status(204).build();
    }


   
}
