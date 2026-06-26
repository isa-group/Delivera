package com.delivera.controller;

import com.delivera.security.AuthRateLimiter;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.auth.CompanyRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterResponse;
import com.delivera.dto.auth.LoginRequest;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.auth.RegisterRequest;
import com.delivera.dto.auth.RegisterResponse;
import com.delivera.dto.common.AvailabilityCheckResponse;
import com.delivera.dto.auth.SwitchCompanyRequest;
import com.delivera.service.AuthService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Endpoints para registro e inicio de sesión")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;
    private final AuthRateLimiter authRateLimiter;

    @Operation(summary = "Iniciar sesión", description = "Autenticación de usuario con email y contraseña")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login exitoso"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request) {
        authRateLimiter.check(httpRequest.getRemoteAddr(), "login");
        LoginResponse response = authService.login(request.identifier(), request.password());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Registrar usuario", description = "Crear una nueva cuenta de usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registro exitoso"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(HttpServletRequest httpRequest, @Valid @RequestBody RegisterRequest request) {
        authRateLimiter.check(httpRequest.getRemoteAddr(), "register");
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Registrar empresa", description = "Crear empresa con su organización y cuenta de administrador")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Empresa registrada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Email o código ya registrado"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    @PostMapping("/register/company")
    public ResponseEntity<CompanyRegisterResponse> registerCompany(HttpServletRequest httpRequest, @Valid @RequestBody CompanyRegisterRequest request) {
        authRateLimiter.check(httpRequest.getRemoteAddr(), "register-company");
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCompany(request));
    }

    @Operation(summary = "Comprobar disponibilidad de nombre de usuario")
    @GetMapping("/check-username")
    public ResponseEntity<AvailabilityCheckResponse> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(new AvailabilityCheckResponse(authService.isUsernameAvailable(username)));
    }

    @Operation(summary = "Cambiar empresa activa")
    @PostMapping("/switch-company")
    public ResponseEntity<LoginResponse> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        String email = securityUtils.getCurrentEmail();
        return ResponseEntity.ok(authService.switchCompany(email, request.companyId()));
    }
}
