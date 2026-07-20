package com.delivera.worker.dto;

import com.delivera.worker.model.WorkerRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull WorkerRole role) {}
