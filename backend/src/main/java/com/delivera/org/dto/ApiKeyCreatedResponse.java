package com.delivera.org.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String prefix,
        String token,
        Instant createdAt
) {}
