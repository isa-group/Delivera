package com.delivera.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.client.core.HttpMicroserviceClient;



@Configuration
public class ClientAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public HttpMicroserviceClient httpClient(WebClient.Builder builder) {
        return new HttpMicroserviceClient(builder);
    }
}
