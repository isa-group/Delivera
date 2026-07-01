package com.delivera.fms.routing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("/").description("Default Server")
                ))
                .info(new Info()
                        .title("FMS Routing Service API")
                        .version("1.0.0")
                        .description("API del servicio de optimizacion de rutas del Fleet Management System"));
    }
}
