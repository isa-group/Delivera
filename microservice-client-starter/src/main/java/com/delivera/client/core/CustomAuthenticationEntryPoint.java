package com.delivera.client.core;

import java.io.IOException;
import java.util.Map;



import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex)
            throws IOException {

        String code = "TOKEN_INVALID";

        Throwable cause = ex.getCause();

        if (cause != null && cause.getMessage() != null) {

            String message = cause.getMessage();

            if (message.contains("expired")) {
                code = "TOKEN_EXPIRED";
            } else if (message.contains("Malformed")) {
                code = "TOKEN_MALFORMED";
            } else if (message.contains("signature")) {
                code = "TOKEN_INVALID_SIGNATURE";
            }
        }

        log.debug("JWT error: {}", ex.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(response.getWriter(),
                Map.of("code", code));
    }
}
