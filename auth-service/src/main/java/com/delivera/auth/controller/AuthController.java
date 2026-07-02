package com.delivera.auth.controller;


import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.dto.ChangePasswordRequest;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginRequest;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.SwitchCompanyRequest;
import com.delivera.auth.model.Credential;
import com.delivera.auth.model.RefreshToken;
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.service.AuthService;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.DeliveraClient;
import com.delivera.auth.service.RefreshTokenService;
import com.delivera.client.config.properties.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DeliveraClient client;
    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final SecurityUtils securityUtils;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.refresh-token.secure}")
    private boolean secureRefreshCookie = false;

   @Value("${app.refresh-token.domain}")
    private String domainRefreshCookie = null;

    @Value("${app.refresh-token.path}")
    private String pathRefreshCookie = null;


    @Autowired
    public AuthController(DeliveraClient client, 
        AuthServiceImpl authService,
        InMemoryAuthRateLimiter authRateLimiter, 
        SecurityUtils securityUtils,
        RefreshTokenService refreshTokenService
    ) {
        this.client = client;
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.securityUtils = securityUtils;
        this.refreshTokenService = refreshTokenService;
    }

    private String getIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = httpRequest.getRemoteAddr();
        }
        return ip;
    }

    private String getUserAgent(HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");

        if (userAgent == null) {
            userAgent = "unknown-agent";
        }

        return userAgent.length() > 1000
            ? userAgent.substring(0, 1000)
            : userAgent;

    }

    private String getDeviceId(HttpServletRequest httpRequest) {
        String device = httpRequest.getHeader("X-Device-Id");
        if (device == null) {
            device = "unknown";
        }
        return device;
    }

    private String getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
    
        for (Cookie cookie : request.getCookies()) {
            if ("refresh_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    private ResponseCookie refreshCookie(String newToken, Integer daysToRefresh) {
       return ResponseCookie.from("refresh_token", newToken)
        .httpOnly(true)
        .secure(secureRefreshCookie) 
        .path(pathRefreshCookie)
        .maxAge(Duration.ofDays(daysToRefresh))
        .sameSite("Lax")
        .domain(domainRefreshCookie)
        .build();
    }

    private ResponseCookie deleteRefreshToken() {
            return  ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(secureRefreshCookie)
            .path(pathRefreshCookie) 
            .maxAge(0)
            .sameSite("Lax")
            .build();

    }
    
    
      


    

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request) {
        String ip = getIp(httpRequest);
        String deviceId = getDeviceId(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        authRateLimiter.check("LOGIN:"+ip);

        Credential credential = authService.login(request.getIdentifier(), request.getPassword(),ip);
        
        
        
        DeliveraOrgContext orgInfo = client.getOrgInfoByUserId(credential.getUserId()).block();
        LoginResponse loginResponse = authService.buildLoginResponse(credential, orgInfo);

        String newToken = refreshTokenService.create(credential, deviceId, userAgent, ip);
        
        ResponseCookie refreshCookie = refreshCookie(newToken, refreshTokenService.getDaysToRefresh());

        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(loginResponse);

        /* 
        return client.getOrgInfoByUserId(credential.getUserId())
        .map( orgInfo -> authService.buildLoginResponse(credential, orgInfo))
        .map(loginResponse -> ResponseEntity.ok(loginResponse));
        */
    }

   

     

    @Operation(summary = "Change active company")
    @PostMapping("/switch-company")
    public ResponseEntity<LoginResponse> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        String email = securityUtils.getCurrentEmail();
        Integer tokenVersion = securityUtils.getTokenVersion();

        Credential credential = authService.getUserCredentialByEmail(email, tokenVersion);
        DeliveraOrgContext orgInfo = client.getOrgSwitchInfo(credential.getUserId(),request.companyId() ).block();
        LoginResponse loginResponse = authService.buildLoginResponse(credential, orgInfo);
        return ResponseEntity.ok(loginResponse);
    }

    @Operation(summary = "Change active company")
    @PutMapping("/password")
    public ResponseEntity<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest  request) {
        UUID userId = securityUtils.getCurrentUserId();
        Integer tokenVersion = securityUtils.getTokenVersion();
        
        Credential credential = authService.changePassword(userId, 
            request.currentPassword(), request.newPassword(), tokenVersion);
        
        DeliveraOrgContext orgInfo = client.getOrgInfoByUserId(credential.getUserId()).block();
        LoginResponse loginResponse = authService.buildLoginResponse(credential, orgInfo);
        return ResponseEntity.ok(loginResponse);
    }

    @Operation(summary = "Refresh jwt")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        HttpServletRequest httpRequest
    ) {
        String token = getRefreshTokenFromCookies(httpRequest);
        String ip = getIp(httpRequest);
        String deviceId = getDeviceId(httpRequest);
        String userAgent = getUserAgent(httpRequest);

        RefreshToken refreshToken = refreshTokenService.getFromToken(token);
        Credential credential = refreshToken.getCredential();

        DeliveraOrgContext orgInfo = client.getOrgInfoByUserId(credential.getUserId()).block();
        LoginResponse loginResponse = authService.buildLoginResponse(credential, orgInfo);

        String newToken = refreshTokenService.refresh(token, deviceId, userAgent, ip);

        ResponseCookie refreshCookie = refreshCookie(newToken, refreshTokenService.getDaysToRefresh());


        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(loginResponse);

    }

    @Operation(summary = "logout ")
    @GetMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest httpRequest
    ) {
        String token = getRefreshTokenFromCookies(httpRequest);
 
        refreshTokenService.removeToken(token);
        
        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE,deleteRefreshToken().toString())
        .build();

    }


   

    
    
  

}
