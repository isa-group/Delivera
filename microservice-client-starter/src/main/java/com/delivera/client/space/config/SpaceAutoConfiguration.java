package com.delivera.client.space.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(SpaceProperties.class)
public class SpaceAutoConfiguration {
}