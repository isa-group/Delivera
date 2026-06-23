package com.delivera.client.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.delivera.client.config.properties.DeliveraProperties;

@Configuration
@EnableConfigurationProperties(DeliveraProperties.class)
public class DeliveraClientAutoConfig {
}
