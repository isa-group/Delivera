package com.delivera.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Instant createdAt,
        boolean isWorker,
        boolean isGlobalAdmin
) {}
