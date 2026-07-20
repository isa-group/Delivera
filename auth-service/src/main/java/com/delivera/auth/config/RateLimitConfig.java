package com.delivera.auth.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;


@ConfigurationProperties(prefix = "app.auth.rate-limit")
@Component
public class RateLimitConfig {

    
    private Map<String, RateLimitRule> rules = new HashMap<>();

    public Map<String, RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(Map<String, RateLimitRule> rules) {
        this.rules = rules;
    }

    
    @Getter
    @Setter
    public static class RateLimitRule {
        private int maxAttempts;
        private long windowMs;

    }

}
