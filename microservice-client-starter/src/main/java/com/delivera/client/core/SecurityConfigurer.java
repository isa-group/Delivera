package com.delivera.client.core;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.delivera.client.config.JwtConverter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecurityConfigurer {

    private final JwtDecoder jwtDecoder;
    private final JwtConverter jwtConverter;
    private final InternalApiKeyFilter internalFilter;

    public SecurityConfigurer(JwtDecoder jwtDecoder,
                              JwtConverter jwtConverter,
                              InternalApiKeyFilter internalFilter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
        this.internalFilter = internalFilter;
    }

  
    public void applyResourceServer(HttpSecurity http) throws Exception {

        if (jwtDecoder != null) {
            http.oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtConverter)
                )
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
            );
        }else {
            log.warn("The jwt filter is not active");
        }

        http.addFilterBefore(
            internalFilter,
            UsernamePasswordAuthenticationFilter.class
        );
    }

    
    public void applyIssuer(HttpSecurity http, JwtTokenParser jwtService) throws Exception {

        http.addFilterBefore(
            new AuthJwtExtractionFilter(jwtService),
            UsernamePasswordAuthenticationFilter.class
        );

        http.addFilterBefore(
            internalFilter,
            UsernamePasswordAuthenticationFilter.class
        );
    }


}

