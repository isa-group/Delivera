package com.delivera.dto.settings;

import java.util.UUID;

import com.delivera.order.model.OrderPriority;

public record CompanySummary(UUID id, String name, String activityType, String logoData, OrderPriority defaultPriority, boolean defaultPriorityLocked) {}
