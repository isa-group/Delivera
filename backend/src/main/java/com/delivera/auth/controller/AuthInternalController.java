package com.delivera.auth.controller;


import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.service.AuthInternalService;




import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


import java.util.UUID;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/auth")
@Tag(name = "Autenticación", description = "Endpoints para registro e inicio de sesión")
public class AuthInternalController {

    private final AuthInternalService authService;

    @Operation(summary = "", description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login exitoso"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos")
    })
    @GetMapping("/context/{userId}")
    public ResponseEntity<DeliveraOrgContext> getContext(@PathVariable UUID userId ) {
        DeliveraOrgContext response = authService.getContextByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/switch-company/{companyId}/user/{userId}")
    public ResponseEntity<DeliveraOrgContext> getContext(@PathVariable UUID companyId ,@PathVariable UUID userId ) {
        DeliveraOrgContext response = authService.getContextByUserIdAndByCompanyId(userId,companyId);
        return ResponseEntity.ok(response);
    }
}
