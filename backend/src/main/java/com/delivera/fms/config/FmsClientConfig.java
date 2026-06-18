package com.delivera.fms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class FmsClientConfig {

    @Value("${app.fms.routing.base-url}")
    private String fmsRoutingBaseUrl;

    @Bean
    public RestClient fmsRoutingClient() {
        return RestClient.builder()
                .baseUrl(fmsRoutingBaseUrl)
                .build();
    }
}
