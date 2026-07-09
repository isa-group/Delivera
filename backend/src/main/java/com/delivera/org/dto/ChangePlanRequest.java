package com.delivera.org.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanRequest(@NotBlank String planCode, boolean force) {}
