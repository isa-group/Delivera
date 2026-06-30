package com.delivera.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestSeed {

        @NotNull
        UUID userId;

        @NotBlank
        @Email
        String email;

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "Username only allows lowercase letters, numbers, hyphens and underscores")
        String username;

    

        @NotBlank
        @Size(min = 8)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
        String password;
}
