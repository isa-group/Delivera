package com.delivera.org.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrgCheckRequest {
    @NotNull
    UUID companyId;
    @NotNull
    UUID orgId;
}
