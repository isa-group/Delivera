package com.delivera.depot.dto;

import java.util.UUID;

import com.delivera.depot.model.OperationalUnit;

public record B2BUnitResponse(UUID id, String name, String type, UUID companyId, String companyName, UUID orgId, String orgName) {

    public static B2BUnitResponse from(OperationalUnit unit) {
        return new B2BUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getType().name(),
                unit.getCompany().getId(),
                unit.getCompany().getName(),
                unit.getCompany().getOrganization().getId(),
                unit.getCompany().getOrganization().getName());
    }
}
