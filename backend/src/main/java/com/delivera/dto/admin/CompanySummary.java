package com.delivera.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record CompanySummary(
        UUID id,
        String name,
        UUID orgId,
        String orgName,
        long workerCount,
        long orderCount,
        Instant createdAt
) {}
