package com.delivera.auth.controller;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.dto.ChangeUsernameRequest;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RegisterRequest;
import com.delivera.auth.dto.RegisterRequestSeed;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.model.Credential;
import com.delivera.auth.service.AuthService;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.tags.Tag;



@RestController
@Tag(name = "Auth Internal", description = "all required features used in other microservices")
@RequestMapping("/internal/auth")
public class AuthInternalController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.refresh-token.secure}")
    private boolean secureRefreshCookie = false;

    @Value("${app.refresh-token.domain}")
    private String domainRefreshCookie = null;

    @Value("${app.refresh-token.path}")
    private String pathRefreshCookie = null;

    @Autowired
    public AuthInternalController(AuthServiceImpl authService,RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    private RefreshCookieData buildRefreshCookieData(String token) {
        return new RefreshCookieData(
            token,
            secureRefreshCookie, 
            domainRefreshCookie, 
            pathRefreshCookie,
            refreshTokenService.getDaysToRefresh()
        );
    }


    @Profile("dev")
    @PostMapping("/seed/register")
    public ResponseEntity<Void> registerSeed(@RequestBody  RegisterRequestSeed registerRequest) {
        authService.createCredentials(
            registerRequest.getUserId(), 
            registerRequest.getEmail(),
             registerRequest.getUsername(), 
             registerRequest.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody  RegisterRequest registerRequest) {
        Credential credential = authService.createCredentials(
            registerRequest.getUserId(), 
            registerRequest.getEmail(),
             registerRequest.getUsername(), 
             registerRequest.getPassword()
        );
        if (registerRequest.getRequestClientData() == null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(
                authService.buildLoginResponse(credential, registerRequest.getContext())
            );
        }
        RequestClientData requestClientData = registerRequest.getRequestClientData();
        UUID companyId =  registerRequest.getContext()!= null? 
            registerRequest.getContext().getCompanyId() 
            : null;
        String token = refreshTokenService.create(
            credential,requestClientData.deviceId() , 
            requestClientData.userAgent(), 
            requestClientData.ip(),
            companyId    
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            authService.buildLoginResponse(
                credential, 
                registerRequest.getContext(),
                buildRefreshCookieData(token)
            )
        );

        
    }

    
    @PutMapping("/username")
    public ResponseEntity<Void> changeUsername(@RequestBody ChangeUsernameRequest request) {
        authService.changeUsername(request.userId(), request.username());
        return ResponseEntity.status(HttpStatus.resolve(204)).build();
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        authService.delete(userId);
        return ResponseEntity.status(HttpStatus.resolve(204)).build();
    }


    @DeleteMapping("/user")
    public ResponseEntity<Void> deleteUsers(
        @RequestBody Set<UUID> userIds
    ) {
        authService.deleteUsers(userIds);
        return ResponseEntity.status(HttpStatus.resolve(204)).build();
    }





}
