package com.delivera.org.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String prefix,
        Instant createdAt,
        Instant revokedAt,
        Instant lastUsedAt
) {}
