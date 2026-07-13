package com.delivera.worker.controller;

import com.delivera.worker.dto.ChangeRoleRequest;
import com.delivera.worker.dto.WorkerInviteRequest;
import com.delivera.worker.dto.WorkerResponse;
import com.delivera.worker.service.WorkerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/workers")
@Tag(name = "Trabajadores", description = "Gestión de trabajadores de la empresa")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Operation(summary = "Listar trabajadores de la empresa")
    @GetMapping
    public ResponseEntity<List<WorkerResponse>> list() {
        return ResponseEntity.ok(workerService.getByCompany());
    }

    @Operation(summary = "Listar trabajadores de la empresa")
    @PostMapping("/required")
    public ResponseEntity<List<WorkerResponse>> listRequired(@RequestBody @Valid Set<UUID> workerIds ) {
        return ResponseEntity.ok(workerService.getRequired(workerIds));
    }


    @Operation(summary = "Invitar trabajador por email con rol")
    @PostMapping("/invite")
    public ResponseEntity<WorkerResponse> invite(@Valid @RequestBody WorkerInviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workerService.invite(req));
    }

    @Operation(summary = "Cambiar rol de un trabajador")
    @PatchMapping("/{id}/role")
    public ResponseEntity<WorkerResponse> changeRole(@PathVariable UUID id,
                                                     @Valid @RequestBody ChangeRoleRequest req) {
        return ResponseEntity.ok(workerService.changeRole(id, req));
    }

    @Operation(summary = "Eliminar trabajador de la empresa")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        workerService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
