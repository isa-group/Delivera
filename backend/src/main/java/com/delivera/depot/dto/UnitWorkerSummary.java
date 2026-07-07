package com.delivera.depot.dto;

import com.delivera.model.Worker;

import java.util.UUID;

public record UnitWorkerSummary(UUID id, String email, String firstName, String lastName, String role) {
    public static UnitWorkerSummary from(Worker w) {
        return new UnitWorkerSummary(
                w.getId(),
                w.getUser().getEmail(),
                w.getUser().getFirstName(),
                w.getUser().getLastName(),
                w.getRole().name());
    }
}
