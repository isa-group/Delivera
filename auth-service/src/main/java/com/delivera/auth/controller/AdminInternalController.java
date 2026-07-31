package com.delivera.auth.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/admin")
public class AdminInternalController {

    private final AuthService authService;

    @DeleteMapping("/auth-service/{userId}")
    public ResponseEntity<Void> deleteAllExceptUserId(@PathVariable @Valid UUID userId) {
        authService.deleteAllExceptUserId(userId);
        return ResponseEntity.status(204).build();
    }

}
