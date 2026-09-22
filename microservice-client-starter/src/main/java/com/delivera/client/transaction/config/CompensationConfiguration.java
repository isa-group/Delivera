package com.delivera.client.transaction.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.delivera.client.transaction.aop.CompensableAspect;

@AutoConfiguration
@EnableAspectJAutoProxy
public class CompensationConfiguration {

    @Bean
    public CompensableAspect compensableAspect() {
        return new CompensableAspect();
    }

}

