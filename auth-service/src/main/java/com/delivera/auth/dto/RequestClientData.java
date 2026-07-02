package com.delivera.auth.dto;

public record RequestClientData (
    String ip,
    String deviceId,
    String userAgent
) {}
