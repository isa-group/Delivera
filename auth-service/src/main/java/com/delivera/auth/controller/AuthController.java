package com.delivera.auth.controller;


import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.service.AuthService;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.DeliveraClient;
import com.delivera.client.config.properties.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DeliveraClient client;
    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final SecurityUtils securityUtils;




    @Autowired
    public AuthController(DeliveraClient client, 
        AuthServiceImpl authService,
        InMemoryAuthRateLimiter authRateLimiter, 
        SecurityUtils securityUtils
    ) {
        this.client = client;
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.securityUtils = securityUtils;
    }

    

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request) {
        authRateLimiter.check("LOGIN:"+httpRequest.getRemoteAddr());

        Credential credential = authService.login(request.getIdentifier(), request.getPassword(),httpRequest.getRemoteAddr());

        return client.getOrgInfoByUserId(credential.getUserId())
        .map( orgInfo -> authService.buildLoginResponse(credential, orgInfo))
        .map(loginResponse -> ResponseEntity.ok(loginResponse));
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

    

  

}
