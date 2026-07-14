package com.delivera.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.delivera.client.config.properties.DeliveraProperties;
import com.delivera.client.core.InternalApiKeyFilter;

@Configuration
@EnableConfigurationProperties(DeliveraProperties.class)
public class DeliveraClientAutoConfig {
    
    @Bean
    @ConditionalOnProperty(
        prefix = "delivera.client.internal-auth",
        name = "filter-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public InternalApiKeyFilter internalApiKeyFilter(DeliveraProperties config) {
        return new InternalApiKeyFilter(config);
    }

}
