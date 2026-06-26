package com.delivera.client.core;


import java.util.UUID;

public interface JwtTokenParser {

    TokenClaims parse(String token);

    record TokenClaims(
        String email,
        String role,
        UUID companyId,
        UUID userId
    ) {}
}
