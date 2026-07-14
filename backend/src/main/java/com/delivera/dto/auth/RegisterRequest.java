package com.delivera.dto.auth;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "Username only allows lowercase letters, numbers, hyphens and underscores")
        String username,

        @NotBlank @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 20)
        String phone,

        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
        String password,

        @Size(max = 500)
        String address,

        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        BigDecimal longitude
) {
    @AssertTrue(message = "MISSING_USER_LOCATION")
    public boolean isLocationPresent() {
        boolean hasAddress = address != null && !address.isBlank();
        boolean hasCoords = latitude != null && longitude != null;
        return hasAddress || hasCoords;
    }
}
