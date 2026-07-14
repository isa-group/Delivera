package com.delivera.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrgUpdateRequest(
        @NotBlank @Size(max = 255)
        String name,

        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String handle
) {}
