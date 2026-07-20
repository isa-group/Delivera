package com.delivera.client.config;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.delivera.client.config.properties.DeliveraProperties;


@Configuration
@ConditionalOnProperty(
    prefix = "delivera.jwt",
    name = "jwks-uri"
)
public class JwtAutoConfig {

    @Bean
    public JwtDecoder jwtDecoder(DeliveraProperties props) {

        String jwksUri = props.getJwt().getJwksUri();

        if (jwksUri == null) {
            throw new IllegalStateException("JWKS URI must be configured");
        }

        return NimbusJwtDecoder
                .withJwkSetUri(jwksUri)
                .build();
    }
}