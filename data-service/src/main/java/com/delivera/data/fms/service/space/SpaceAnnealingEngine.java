package com.delivera.data.fms.service.space;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.space.service.SpaceManagment;

@Service
public class SpaceAnnealingEngine extends AbstractSpaceFeature {
    
    public SpaceAnnealingEngine(SpaceManagment space) {
        super(space);
    }

    @Override
    protected String feature() {
        return "annealingSolverExecutions";
    }

}

