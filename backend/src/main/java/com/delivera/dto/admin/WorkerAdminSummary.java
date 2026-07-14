package com.delivera.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record WorkerAdminSummary(
        UUID id,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        String companyName,
        String orgName,
        Instant createdAt
) {}
