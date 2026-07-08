package com.delivera.data.worker.dto;



import com.delivera.data.worker.model.WorkerRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull WorkerRole role) {}
