package com.delivera.controller;

import com.delivera.org.dto.ApiKeyCreateRequest;
import com.delivera.org.dto.ApiKeyCreatedResponse;
import com.delivera.org.dto.ApiKeyResponse;
import com.delivera.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/settings/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Gestión de API keys de empresa")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @Operation(summary = "Listar API keys")
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> list() {
        return ResponseEntity.ok(apiKeyService.list());
    }

    @Operation(summary = "Generar nueva API key")
    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> create(@Valid @RequestBody ApiKeyCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.create(req));
    }

    @Operation(summary = "Revocar una API key")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
