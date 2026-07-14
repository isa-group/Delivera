package com.delivera.client.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.core.InternalApiKeyFilter;
import com.delivera.client.core.SecurityConfigurer;


@Configuration
public class SecurityAutoConfig {

    
    @Bean
    public SecurityUtils securityUtils() {
        return new SecurityUtils();
    }

    @Bean
    public JwtConverter jwtConverter() {
        return new JwtConverter();
    }

    
    @Bean
    public SecurityConfigurer securityConfigurer(
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            JwtConverter jwtConverter,
            InternalApiKeyFilter internalFilter) {
        return new SecurityConfigurer(
            jwtDecoderProvider.getIfAvailable(), 
            jwtConverter, 
            internalFilter);
    }


  
}




