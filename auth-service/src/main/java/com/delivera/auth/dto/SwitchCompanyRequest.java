package com.delivera.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchCompanyRequest(@NotNull UUID companyId) {}
