package com.delivera.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.SpaceManagment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpaceCompanies {

    private final SpaceManagment space;

     /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "EVALUATE"
     * @param userId, quantity
     */
     public void companiesConsumption(String userId, Integer quantity, Boolean server) {
        space.executeConsumptionWithMaxLimit(userId, "companies", quantity, server);
    }

    public void addCompany(String userId) {
        companiesConsumption(userId, 1, false);
    }

    public void deleteCompany(String userId) {
        companiesConsumption(userId, -1, true);
    }


}
