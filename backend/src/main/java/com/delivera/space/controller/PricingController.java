package com.delivera.space.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.exception.CompanyContextException;
import com.delivera.space.dto.ContractChange;
import com.delivera.space.service.SpaceContracts;

import io.github.isagroup.spaceclient.types.Contract;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pricing")
public class PricingController {

    private final SpaceContracts spaceContracts;
    private final SecurityUtils securityUtils;
   

    @GetMapping
    public ResponseEntity<Object> pricing() {
        return ResponseEntity.ok(
            spaceContracts.getCurrentPricing()
        );
    }

    @GetMapping("/contract")
    public ResponseEntity<Contract> contract() {
        UUID orgId = securityUtils.getCurrentOrgId();
        if (orgId == null) {
            throw new CompanyContextException();
        }
      
        return ResponseEntity.ok(
            spaceContracts.getContract(orgId.toString())
        );
    }

    @PostMapping("/contract")
    public ResponseEntity<Contract> update(@RequestBody @Valid ContractChange newContract) {
        UUID orgId = securityUtils.getCurrentOrgId();
        if (orgId == null) {
            throw new CompanyContextException();
        }
        return ResponseEntity.ok(spaceContracts.updateContract(orgId.toString(), newContract));
    }

    @PutMapping("/contract/autoRenew")
    public ResponseEntity<Void> cancelAutoRenew() {
        UUID orgId = securityUtils.getCurrentOrgId();
        if (orgId == null) {
            throw new CompanyContextException();
        }
        spaceContracts.toggleAutoRenewContract(orgId.toString());
        return ResponseEntity.status(204).build();
    }

}
