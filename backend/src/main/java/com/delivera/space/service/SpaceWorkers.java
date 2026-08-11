package com.delivera.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.SpaceManagment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpaceWorkers {

    private final SpaceManagment space;

    
    public void workerConsumption(String userId, Integer quantity, Boolean server) {
        space.executeConsumptionWithMaxLimit(userId,"workers" , quantity, server);
    }

    public void addWorker(String userId) {
        workerConsumption(userId, 1, false);
    }

    public void deleteWorker(String userId) {
        workerConsumption(userId, -1, true);
    }

    public void deleteAllWorkers(String userId, Integer numWorkers, Boolean server) {
        workerConsumption(userId, -numWorkers, true);
    }

}
