package com.delivera.client.space.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.delivera.client.space.service.SpaceManagment;

import io.github.isagroup.spaceclient.SpaceClient;

@AutoConfiguration
@EnableConfigurationProperties(SpaceProperties.class)
public class SpaceAutoConfiguration {

    @Bean
    @ConditionalOnBean(SpaceClient.class)
    public SpaceManagment spaceManagment(
        SpaceClient spaceClient
    ){
        return new SpaceManagment(spaceClient);
    }
}