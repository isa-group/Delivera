package com.delivera.data.activity.dto;

public record ActivityMetricsResponse(
        String period,
        long totalOrders,
        long completedOrders,
        long cancelledOrders,
        long activeOrders
) {}
