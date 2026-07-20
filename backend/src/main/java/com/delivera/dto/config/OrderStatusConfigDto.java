package com.delivera.dto.config;

import java.util.List;

import com.delivera.order.model.OrderStatusConfig;

public record OrderStatusConfigDto(
        String status,
        String uiSeverity,
        List<String> allowedTransitions,
        boolean terminal,
        int sortOrder
) {
    public static OrderStatusConfigDto from(OrderStatusConfig c) {
        return new OrderStatusConfigDto(
                c.getStatus(), c.getUiSeverity(),
                c.getAllowedTransitionsList(), c.isTerminal(), c.getSortOrder()
        );
    }
}
