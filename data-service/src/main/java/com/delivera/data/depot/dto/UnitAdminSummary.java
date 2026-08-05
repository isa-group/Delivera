package com.delivera.data.depot.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.delivera.data.depot.model.UnitType;

public record UnitAdminSummary(
        UUID id,
        String name,
        UnitType type,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID companyId,
        UUID orgId
) {}
