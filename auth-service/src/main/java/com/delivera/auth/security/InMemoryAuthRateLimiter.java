package com.delivera.auth.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.delivera.auth.exception.RateLimitExceededException;

@Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {

     private final int maxAttempts;
    private final long windowMs;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public InMemoryAuthRateLimiter(@Value("${app.auth.rate-limit.max-attempts:5}") int maxAttempts,
                           @Value("${app.auth.rate-limit.window-ms:60000}") long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    public void check(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > windowMs) {
                window.pollFirst();
            }
            if (window.size() >= maxAttempts) {
                throw new RateLimitExceededException();
            }
            window.addLast(now);
        }
    }

}
