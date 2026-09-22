package com.delivera.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.space.service.SpaceManagment;


@Service
public class SpaceWorkers extends AbstractSpaceFeature {

    public SpaceWorkers(SpaceManagment space) {
        super(space);
    }

    @Override
    protected String feature() {
        return "workers";
    }

}
