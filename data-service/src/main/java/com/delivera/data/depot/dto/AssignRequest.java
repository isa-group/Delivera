package com.delivera.data.depot.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRequest {

    @NotNull
    private UUID userId;
    private UUID companyId;
    @NotNull
    private UUID workerId;
}
