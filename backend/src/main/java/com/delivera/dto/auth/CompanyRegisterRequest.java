package com.delivera.dto.auth;

import jakarta.validation.constraints.*;

public record CompanyRegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
        String password,

        @NotBlank @Size(max = 255)
        String orgName,

        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String orgHandle,

        @NotBlank @Size(max = 255)
        String companyName,

        @NotBlank @Size(max = 50)
        String activityType,

        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z0-9_-]+$")
        String username,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 20)
        String phone
) {}
