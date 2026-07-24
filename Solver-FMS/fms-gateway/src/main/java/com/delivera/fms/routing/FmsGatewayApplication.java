package com.delivera.fms.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FmsGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FmsGatewayApplication.class, args);
    }
}
