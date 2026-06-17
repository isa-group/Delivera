package com.delivera.auth.security;

import java.io.IOException;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    
    @Value("${app.internal.api-key}")
    private String expectedKey;

    @Value("${app.api-prefix}")
    private String api;

    private final String FORWARD_HEADER = "X-Forwarded-For";

    private final String KEY_HEADER = "X-Internal-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        String ip = getClientIp(request);

        if (path.startsWith("/auth/internal")) {

            String key = request.getHeader(KEY_HEADER);

            // MessageDigest IS USED TO AVOID TIMING ATTACK
            if (key == null || !MessageDigest.isEqual(expectedKey.getBytes(), key.getBytes())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                log.warn("Unauthorized internal call to {} by IP: {}", path, ip);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {

        String forwardHeader = request.getHeader(FORWARD_HEADER);
    
        if (forwardHeader != null && !forwardHeader.isEmpty()) {
            return forwardHeader.split(",")[0];
        }
    
        return request.getRemoteAddr();
    }
    

}
