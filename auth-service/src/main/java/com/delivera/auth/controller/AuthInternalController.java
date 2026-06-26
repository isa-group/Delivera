package com.delivera.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.dto.RegisterRequest;
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

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody  RegisterRequest registerRequest) {
        System.out.println(registerRequest.getUserId()+","+
        registerRequest.getEmail()+","+
         registerRequest.getUsername()+","+
         registerRequest.getPassword());
        authService.createCredentials(
            registerRequest.getUserId(), 
            registerRequest.getEmail(),
             registerRequest.getUsername(), 
             registerRequest.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
