package com.delivera.client.core;

import java.io.IOException;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.delivera.client.config.properties.DeliveraProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(
    prefix = "delivera.client.internal-auth",
    name = "filter-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final DeliveraProperties config;
    private final AntPathMatcher matcher = new AntPathMatcher();

    
    @Value("${app.api-prefix:}")
    private String apiPrefix;


    public InternalApiKeyFilter(DeliveraProperties config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String socket = request.getRemoteAddr()+":"+request.getRemotePort();
        var internalAuth = config.getClient().getInternalAuth();

        if (!internalAuth.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        String normalizedPath = path;

        if (apiPrefix != null && path.startsWith(apiPrefix)) {
            normalizedPath = path.substring(apiPrefix.length());
        }

        if (!normalizedPath.startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        String serviceName = request.getHeader(internalAuth.getServiceHeaderName());
        String apiKey = request.getHeader(internalAuth.getHeaderName());

        if (serviceName == null || apiKey == null) {
            forbidden(response, "Missing headers", path, socket);
            return;
        }

        var service = config.getSecurity().getServices().get(serviceName);

        if (service == null) {
            forbidden(response, "Unknown service", path, socket);
            return;
        }

        var expectedKey = service.getApiInternalKey();
       
        if (!MessageDigest.isEqual(expectedKey.getBytes(), apiKey.getBytes())) {
            forbidden(response, "Invalid API key", path, socket);
            return;
        }

        var allowedPaths = service.getAllowedPaths();
        
        if ((allowedPaths == null || allowedPaths.isEmpty())
            && config.getSecurity().isDefaultDeny()) {

            forbidden(response, "No allowed paths configured for service", path, socket);
            return;
        }


        if (allowedPaths != null && !allowedPaths.isEmpty()) {
            final String resolvePath = normalizedPath;
            boolean allowed = allowedPaths
                .stream()
                .anyMatch(pattern -> matcher.match(pattern, resolvePath));

            if (!allowed) {
                forbidden(response, "Service not allowed to access this path", path, socket);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void forbidden(HttpServletResponse response, String msg, String path, String socket) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(msg);
        log.warn("Unauthorized internal call to {} by IP: {}", path, socket);
    }
}

