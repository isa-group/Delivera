package com.delivera.data.order.dto;

public record RequestClientData (
    String ip,
    String deviceId,
    String userAgent
) {}
