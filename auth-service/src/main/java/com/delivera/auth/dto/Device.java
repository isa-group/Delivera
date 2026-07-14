package com.delivera.auth.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Device {

    private String ip;

    private String userAgent;

    private Boolean revoked = false;

    private Boolean suspicious = false;

    private Instant lastUsed;

    private Instant createdAt; 

    private Instant maxExpiredAt;

    private Boolean currentSession;
}
