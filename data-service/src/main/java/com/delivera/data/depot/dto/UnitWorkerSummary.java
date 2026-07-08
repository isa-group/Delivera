package com.delivera.data.depot.dto;

import java.util.UUID;

import com.delivera.data.worker.model.Worker;



public record UnitWorkerSummary(
    UUID id, 
    // TODO:String email, String firstName, String lastName, 
    String role) {
    public static UnitWorkerSummary from(Worker w) {
        return new UnitWorkerSummary(
                w.getId(),
                //w.getUser().getEmail(),
                //w.getUser().getFirstName(),
                //w.getUser().getLastName(),
                w.getRole().name());
    }
}
