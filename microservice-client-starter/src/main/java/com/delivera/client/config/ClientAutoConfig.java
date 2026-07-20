package com.delivera.client.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.client.config.properties.DeliveraProperties;
import com.delivera.client.core.HttpMicroserviceClient;
import com.delivera.client.core.MtlsMicroserviceClient;
import com.delivera.client.core.SmartMicroserviceClient;



@Configuration
public class ClientAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public HttpMicroserviceClient httpClient(WebClient.Builder builder) {
        return new HttpMicroserviceClient(builder);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public SmartMicroserviceClient microserviceClient(
        HttpMicroserviceClient httpClient,
        @Autowired(required = false) MtlsMicroserviceClient mtlsClient,
        DeliveraProperties config
    ) {
        return new SmartMicroserviceClient(httpClient, mtlsClient, config);
    }

}
