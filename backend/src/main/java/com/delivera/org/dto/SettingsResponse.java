package com.delivera.org.dto;

import java.util.UUID;

import com.delivera.order.model.OrderPriority;

public record SettingsResponse(
        UUID orgId,
        String orgName,
        String orgHandle,
        UUID companyId,
        String companyName,
        String activityType,
        OrderPriority defaultPriority,
        boolean defaultPriorityLocked
) {}
