package com.delivera.auth.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.delivera.auth.config.RateLimitConfig;
import com.delivera.auth.config.RateLimitConfig.RateLimitRule;
import com.delivera.auth.exception.RateLimitExceededException;

@Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {

     
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final RateLimitConfig config;

    @Autowired
    public InMemoryAuthRateLimiter(RateLimitConfig config) {
        this.config = config;
    }
    
    public void check(String key) {
        String type = key.split(":")[0];
        RateLimitRule rule = config.getRules().get(type);

        if (rule == null) return;

        long now = System.currentTimeMillis();
        Deque<Long> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > rule.getWindowMs()) {
                window.pollFirst();
            }

            if (window.size() >= rule.getMaxAttempts()) {
                throw new RateLimitExceededException();
            }

            window.addLast(now);
        }
    }


}
