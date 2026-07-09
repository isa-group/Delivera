package com.delivera.org.dto;

public record SubscriptionUsageResponse(
        String planCode,
        String planName,
        ResourceUsage units,
        ResourceUsage workers,
        ResourceUsage ordersThisMonth,
        ResourceUsage loyalUsers,
        ResourceUsage companies
) {
    public record ResourceUsage(long current, int max) {
        public boolean isUnlimited() { return max == -1; }
    }
}
