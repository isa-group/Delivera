package com.delivera.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.SpaceManagment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpaceLoyalUsers {

    private final SpaceManagment space;

    public void loyalUserConsumption(String userId, Integer quantity, Boolean server) {
        space.executeConsumptionWithMaxLimit(userId,"loyalUsers" , quantity, server);
    }

    public void addLoyalUser(String userId) {
        loyalUserConsumption(userId, 1, false);
    }

    public void deleteLoyalUser(String userId) {
        loyalUserConsumption(userId, -1, true);
    }

    public void deleteLoyalUser(String userId, Integer numLoyalUsers) {
        loyalUserConsumption(userId, -numLoyalUsers, true);
    }


}
