package com.delivera.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyCreateRequest(
        @NotBlank @Size(max = 255)
        String name,

        @NotBlank @Size(max = 50)
        String activityType
) {}
