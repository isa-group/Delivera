package com.delivera.data.auth.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.delivera.data.order.dto.RefreshCookieData;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AuthService {

    @Value("${app.gateway.enabled}")
    private Boolean activeGateway = false;

     public String getIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || activeGateway) {
            ip = httpRequest.getRemoteAddr();
        }
        ip = ip.split(",")[0].trim();
        return ip;
    }

    public String getUserAgent(HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");

        if (userAgent == null) {
            userAgent = "unknown-agent";
        }

        return userAgent.length() > 1000
            ? userAgent.substring(0, 1000)
            : userAgent;

    }

    public String getDeviceId(HttpServletRequest httpRequest) {
        String device = httpRequest.getHeader("X-Device-Id");
        if (device == null) {
            device = "unknown";
        }
        return device;
    }

    public ResponseCookie refreshCookie(RefreshCookieData refreshCookieData) {
        return ResponseCookie.from("refresh_token", refreshCookieData.token())
            .httpOnly(true)
            .secure(refreshCookieData.secureRefreshCookie()) 
            .path(refreshCookieData.pathRefreshCookie())
            .maxAge(Duration.ofDays(refreshCookieData.daysToRefresh()))
            .sameSite("Lax")
            .domain(refreshCookieData.domainRefreshCookie())
            .build();
    }
    
}
