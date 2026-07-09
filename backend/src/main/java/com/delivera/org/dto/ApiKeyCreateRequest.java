package com.delivera.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @NotBlank @Size(max = 100)
        String name
) {}
