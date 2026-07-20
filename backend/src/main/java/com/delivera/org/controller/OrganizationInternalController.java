package com.delivera.org.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.org.dto.OrgCheckRequest;
import com.delivera.org.service.CompanyService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/organization")
@Tag(name = "Organizations", description = "Internal organization endpoints")
public class OrganizationInternalController {

    private final CompanyService companyService;

    @GetMapping("/check")
    public ResponseEntity<Boolean> checkCompanyBelongToOrganization(@Valid @RequestBody OrgCheckRequest OrgCheckRequest) {
        return ResponseEntity.ok()
            .body(
                companyService.checkCompanyBelongsToOrganization(OrgCheckRequest)
            );
    }

    
}
