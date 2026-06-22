package com.delivera.auth.security;

public interface AuthRateLimiter {

    public void check(String key);
}
