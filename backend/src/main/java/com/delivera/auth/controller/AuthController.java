package com.delivera.auth.controller;

import com.delivera.security.AuthRateLimiter;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.service.AuthService;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.auth.CompanyRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterResponse;
import com.delivera.dto.auth.RegisterRequest;
import com.delivera.dto.auth.RegisterResponse;
import com.delivera.dto.common.AvailabilityCheckResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;


import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    

    
  

    @Operation(summary = "Registrar usuario", description = "Crear una nueva cuenta de usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registro exitoso"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(HttpServletRequest httpRequest, @Valid @RequestBody RegisterRequest request) {
        String ip = authService.getIp(httpRequest);
        String deviceId = authService.getDeviceId(httpRequest);
        String userAgent = authService.getUserAgent(httpRequest);
        RequestClientData requestClientData = new RequestClientData(ip, deviceId, userAgent);
        authRateLimiter.check(ip, "register");
     
        RegisterResponse response = authService.register(request, requestClientData);
        RefreshCookieData refreshCookieData = response.getRefreshCookieData();
        response.setRefreshCookieData(null);
        ResponseCookie refreshCookie = authService.refreshCookie(refreshCookieData);

        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(response);
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
        String ip = authService.getIp(httpRequest);
        String deviceId = authService.getDeviceId(httpRequest);
        String userAgent = authService.getUserAgent(httpRequest);
        RequestClientData requestClientData = new RequestClientData(ip, deviceId, userAgent);
        authRateLimiter.check(httpRequest.getRemoteAddr(), "register-company");
       
        CompanyRegisterResponse response = authService.registerCompany(request,requestClientData);
        RefreshCookieData refreshCookieData = response.refreshCookieData();
        response = response.deleteRefreshCookie();
        ResponseCookie refreshCookie = authService.refreshCookie(refreshCookieData);

        return ResponseEntity.status(201)
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(response);
    }

    @Operation(summary = "Comprobar disponibilidad de nombre de usuario")
    @GetMapping("/check-username")
    public ResponseEntity<AvailabilityCheckResponse> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(new AvailabilityCheckResponse(authService.isUsernameAvailable(username)));
    }

    /* 
    @Operation(summary = "Cambiar empresa activa")
    @PostMapping("/switch-company")
    public ResponseEntity<LoginResponse> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        String email = securityUtils.getCurrentEmail();
        return ResponseEntity.ok(authService.switchCompany(email, request.companyId()));
    }
 */
}
