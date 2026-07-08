package com.delivera.data.worker.dto;

import java.time.Instant;
import java.util.UUID;

import com.delivera.data.worker.model.Worker;



public record WorkerResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        Instant createdAt,
        String tempPassword) {

    public static WorkerResponse from(Worker w) {
        return new WorkerResponse(
                w.getId(),
                // TODO: .....
                //w.getUser().getEmail(),
                //w.getUser().getFirstName(),
                //w.getUser().getLastName(),
                null,
                null,
                null,
                w.getRole().name(),
                w.getCreatedAt(),
                null);
    }

    public static WorkerResponse withTemp(Worker w, String tempPassword) {
        return new WorkerResponse(
                w.getId(),
                // TODO: .....
                //w.getUser().getEmail(),
                //w.getUser().getFirstName(),
                //w.getUser().getLastName(),
                null,
                null,
                null,
                w.getRole().name(),
                w.getCreatedAt(),
                tempPassword);
    }
}
