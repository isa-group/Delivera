package com.delivera.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record OrderAdminSummary(
        UUID id,
        String reference,
        String status,
        String orderType,
        String companyName,
        String orgName,
        Instant createdAt
) {}
