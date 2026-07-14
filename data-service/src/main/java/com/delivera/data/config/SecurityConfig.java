package com.delivera.data.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.delivera.client.core.SecurityConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN      = "COMPANY_ADMIN";
    private static final String ANALYST    = "ANALYST";
    private static final String OPERATOR   = "OPERATOR";
    private static final String LOYAL_USER = "LOYAL_USER";
    private static final String UNITS_ALL  = "/units/**";

    private static final String[] SWAGGER_PATHS = {
        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**"
    };

    @Value("${app.api-prefix}")
    private String api;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, 
                        SecurityConfigurer configurer) throws Exception {
        
        configurer.applyResourceServer(http);
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(SWAGGER_PATHS).permitAll();
                // seed
                auth.requestMatchers(HttpMethod.POST, api+"/internal/units/seed/organizations/*/companies/*").permitAll();
                auth.requestMatchers(HttpMethod.POST, api+"/internal/units/*/seed/assign").permitAll();
                
                auth.requestMatchers(HttpMethod.POST, api+"/internal/units/*/seed/assign").permitAll();
                auth.requestMatchers(HttpMethod.POST, api+"/internal/vehicles/seed/companies/*").permitAll();
                // internal
                auth.requestMatchers(HttpMethod.DELETE, api+"/internal/units/companies/*").permitAll();
                auth.requestMatchers(HttpMethod.GET, api+"/internal/vehicles/companies/*").permitAll();

                auth.requestMatchers(HttpMethod.GET, api+"/units" ).hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.POST, api+"/units" ).hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.GET, api+"/units/*" ).hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.PUT, api + "/units/*").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + "/units/*").hasRole(ADMIN);

                auth.requestMatchers(HttpMethod.GET, api + "/units/*/workers").hasAnyRole(ADMIN, ANALYST);
                auth.requestMatchers(HttpMethod.POST, api + "/units/*/workers").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + "/units/*/workers/*").hasRole(ADMIN);
                /*TODO: PREGUNTAR*/
                auth.requestMatchers(api + "/vehicles/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);

                auth.anyRequest().denyAll();
            });
            

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList()
        );
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

