package com.delivera.data.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.space.service.SpaceManagment;

@Service
public class SpaceVehicles extends AbstractSpaceFeature {
    
    public SpaceVehicles(SpaceManagment space) {
        super(space);
    }

    @Override
    protected String feature() {
        return "vehicles";
    }

   



}
