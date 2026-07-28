package com.delivera.dto.activity;

public record ActivityMetricsResponse(
        String period,
        long newLoyalUsers
) {}
