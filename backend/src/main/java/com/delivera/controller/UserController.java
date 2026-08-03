package com.delivera.controller;

import com.delivera.dto.common.MessageResponse;
import com.delivera.dto.user.ChangePasswordRequest;
import com.delivera.dto.user.ProfileResponse;
import com.delivera.dto.user.UpdateProfileRequest;
import com.delivera.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
@Tag(name = "Usuario", description = "Endpoints para gestión de perfil de usuario")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Obtener perfil", description = "Obtiene la información del perfil del usuario autenticado")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil obtenido exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        ProfileResponse profile = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Actualizar perfil", description = "Actualiza la información del perfil del usuario")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(Authentication authentication,
                                                         @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse profile = userService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Actualizar avatar", description = "Guarda la imagen de perfil del usuario como base64")
    @PutMapping("/avatar")
    public ResponseEntity<ProfileResponse> updateAvatar(Authentication authentication,
                                                        @RequestBody java.util.Map<String, String> body) {
        ProfileResponse profile = userService.updateAvatar(authentication.getName(), body.get("data"));
        return ResponseEntity.ok(profile);
    }
}
