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
}
