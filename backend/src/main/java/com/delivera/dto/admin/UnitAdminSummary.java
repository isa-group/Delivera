package com.delivera.dto.admin;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitAdminSummary(
        UUID id,
        String name,
        String type,
        BigDecimal latitude,
        BigDecimal longitude,
        String companyName,
        String orgName
) {}
