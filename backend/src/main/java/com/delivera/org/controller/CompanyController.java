package com.delivera.org.controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.org.service.CompanyService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/companies")
@Tag(name = "Companies", description = "Company's endpoints")
public class CompanyController {

    private final CompanyService companyService;


    @PostMapping("/names")
    public ResponseEntity<Map<UUID,String>> getByIds(@NotEmpty @RequestBody Set<UUID> companyIds) {
        return ResponseEntity.ok().body(
            companyService.getCompanyNames(companyIds)
        );
    }
}
