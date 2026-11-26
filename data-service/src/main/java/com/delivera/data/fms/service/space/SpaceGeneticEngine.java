package com.delivera.data.fms.service.space;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.space.service.SpaceManagment;

@Service
public class SpaceGeneticEngine extends AbstractSpaceFeature {
    
    public SpaceGeneticEngine(SpaceManagment space) {
        super(space);
    }

    @Override
    protected String feature() {
        return "geneticSolverExecutions";
    }

}

