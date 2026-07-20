package com.delivera.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record ChangeUsernameRequest(
    @NotBlank 
    String username, 
    UUID userId
) {}
