package com.delivera.auth.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
import com.delivera.auth.dto.RegisterRequest;
import com.delivera.auth.dto.RegisterRequestSeed;
import com.delivera.auth.model.Credential;
import com.delivera.auth.service.AuthService;
import com.delivera.auth.service.AuthServiceImpl;



@RestController
@RequestMapping("/internal/auth")
public class AuthInternalController {

    private final AuthService authService;

    @Autowired
    public AuthInternalController(AuthServiceImpl authService) {
        this.authService = authService;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(
            authService.buildLoginResponse(credential, registerRequest.getContext())
        );
    }

    
    @PutMapping("/username")
    public ResponseEntity<Void> changeUsername(@RequestBody ChangeUsernameRequest request) {
        authService.changeUsername(request.userId(), request.username());
        return ResponseEntity.status(HttpStatus.resolve(204)).build();
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        System.out.println(userId);
        authService.delete(userId);
        return ResponseEntity.status(HttpStatus.resolve(204)).build();
    }





}
