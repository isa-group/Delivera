package com.delivera.space.service;

import org.springframework.stereotype.Service;

import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.space.service.SpaceManagment;



@Service
public class SpaceCompanies extends AbstractSpaceFeature {

    
   public SpaceCompanies(SpaceManagment space) {
    super(space);
   }

    @Override
    protected String feature() {
        return "companies";
    }


}
