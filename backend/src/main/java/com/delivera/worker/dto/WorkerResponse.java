package com.delivera.worker.dto;

import java.time.Instant;
import java.util.UUID;

import com.delivera.worker.model.Worker;

public record WorkerResponse(
        UUID id,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        Instant createdAt,
        String tempPassword) {

    public static WorkerResponse from(Worker w) {
        return new WorkerResponse(
                w.getId(),
                w.getUser().getId(),
                w.getUser().getEmail(),
                w.getUser().getFirstName(),
                w.getUser().getLastName(),
                w.getRole().name(),
                w.getCreatedAt(),
                null);
    }

    public static WorkerResponse withTemp(Worker w, String tempPassword) {
        return new WorkerResponse(
                w.getId(),
                w.getUser().getId(),
                w.getUser().getEmail(),
                w.getUser().getFirstName(),
                w.getUser().getLastName(),
                w.getRole().name(),
                w.getCreatedAt(),
                tempPassword);
    }
}
