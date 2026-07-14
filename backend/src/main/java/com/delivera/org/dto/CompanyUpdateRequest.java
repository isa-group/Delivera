package com.delivera.org.dto;

import com.delivera.order.model.OrderPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyUpdateRequest(
        @NotBlank @Size(max = 255)
        String name,

        @NotBlank @Size(max = 50)
        String activityType,

        OrderPriority defaultPriority,

        boolean defaultPriorityLocked
) {}
