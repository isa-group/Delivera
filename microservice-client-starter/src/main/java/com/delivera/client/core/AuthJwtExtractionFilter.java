package com.delivera.client.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class AuthJwtExtractionFilter extends OncePerRequestFilter {

    private final JwtTokenParser jwtService;

    public AuthJwtExtractionFilter(JwtTokenParser jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7);

            try {
                JwtTokenParser.TokenClaims claims =
                        jwtService.parse(token);

                List<SimpleGrantedAuthority> authorities =
                        claims.role() != null
                                ? List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                                : List.of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                claims.email(),
                                null,
                                authorities
                        );


                UUID companyId = claims.companyId();
                authentication.setDetails(companyId);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}