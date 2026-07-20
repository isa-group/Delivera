package com.delivera.client.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public class JwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

   
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        String role = jwt.getClaim("role");
        String email = jwt.getClaim("email");
        String companyId = jwt.getClaim("companyId");

        List<GrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : List.of();

        JwtAuthenticationToken auth =
                new JwtAuthenticationToken(jwt, authorities, email); 

        if (companyId != null) {
            auth.setDetails(UUID.fromString(companyId));
        }

        return auth;
    }

}