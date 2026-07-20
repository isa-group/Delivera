package com.delivera.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
        @NotBlank
        String identifier;

        @NotBlank
        String password;
}
