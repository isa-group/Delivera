package com.delivera.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidatePassword(
        @NotBlank
        String password
) {}
