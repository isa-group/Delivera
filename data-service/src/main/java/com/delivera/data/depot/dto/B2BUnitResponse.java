package com.delivera.data.depot.dto;

import java.util.UUID;

import com.delivera.data.depot.model.UnitType;


public record B2BUnitResponse(
    UUID id, 
    String name, 
    UnitType type, 
    UUID companyId,
    UUID orgId
) {
    /* 
    public static B2BUnitResponse from(OperationalUnit unit) {
        return new B2BUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getType().name(),
                unit.getCompanyId()
                //unit.getCompany().getName(),
                //unit.getCompany().getOrganization().getId(),
                //unit.getCompany().getOrganization().getName()
            );
    }*/
}
