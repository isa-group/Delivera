package com.delivera.auth.dto;

import java.util.UUID;

public record LoginResponse (
        String token,
        String email,
        UUID companyId,
        String role,
        String companyName,
        String orgHandle,
        String orgName
){}

        
