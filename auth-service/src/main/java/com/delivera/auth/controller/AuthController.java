package com.delivera.auth.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginRequest;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.exception.ForbiddenException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.security.jwt.JwtService;
import com.delivera.auth.service.AuthService;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.DeliveraClient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DeliveraClient client;
    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final JwtService jwtService;




    @Autowired
    public AuthController(DeliveraClient client, 
        AuthServiceImpl authService,InMemoryAuthRateLimiter authRateLimiter,  
        JwtService jwtService) {
        this.client = client;
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.jwtService = jwtService;
    }

    

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request) {
        authRateLimiter.check("LOGIN:"+httpRequest.getRemoteAddr());

        Credential credential = authService.login(request.getIdentifier(), request.getPassword(),httpRequest.getRemoteAddr());

        return client.getOrgInfo(credential.getUserId())
        .map( orgInfo -> buildLoginResponse(credential, orgInfo))
        .map(loginResponse -> ResponseEntity.ok(loginResponse));
    }

    private LoginResponse buildLoginResponse(Credential credential, DeliveraOrgContext orgInfo) {
        String token = jwtService.generateToken(
            credential.getUserId(),
            credential.getEmail(), 
            orgInfo.getCompanyId() , 
            orgInfo.getRole(), 
            credential.getTokenVersion()
        );

        return new LoginResponse(
            token, 
            credential.getEmail(),
            orgInfo.getCompanyId(), 
            orgInfo.getRole(), 
            orgInfo.getCompanyName(), 
            orgInfo.getOrgHandle(), 
            orgInfo.getOrgName()
        );
        
    }
  

}
