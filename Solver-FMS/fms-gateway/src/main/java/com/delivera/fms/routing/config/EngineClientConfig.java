package com.delivera.fms.routing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class EngineClientConfig {

    @Value("${fms.engines.greedy.url}")
    private String greedyEngineUrl;

    @Value("${fms.engines.random.url}")
    private String randomEngineUrl;

    @Value("${fms.engines.genetic.url}")
    private String geneticEngineUrl;

    @Bean("greedyWebClient")
    public WebClient greedyWebClient(WebClient.Builder builder) {
        return builder.baseUrl(greedyEngineUrl).build();
    }

    @Bean("randomWebClient")
    public WebClient randomWebClient(WebClient.Builder builder) {
        return builder.baseUrl(randomEngineUrl).build();
    }

    @Bean("geneticWebClient")
    public WebClient geneticWebClient(WebClient.Builder builder) {
        return builder.baseUrl(geneticEngineUrl).build();
    }
}
