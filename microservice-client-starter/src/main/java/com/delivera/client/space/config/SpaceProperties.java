package com.delivera.client.space.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.space")
public class SpaceProperties {

    private String service;
    
}
