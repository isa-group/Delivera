package com.delivera.dto.config;

import com.delivera.order.model.OrderPriorityConfig;

public record OrderPriorityConfigDto(String priority, String uiSeverity, int sortOrder) {
    public static OrderPriorityConfigDto from(OrderPriorityConfig c) {
        return new OrderPriorityConfigDto(c.getPriority(), c.getUiSeverity(), c.getSortOrder());
    }
}
