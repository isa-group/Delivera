package com.delivera.data.worker.dto;


import com.delivera.data.worker.model.WorkerRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerInviteRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull WorkerRole role) {}
