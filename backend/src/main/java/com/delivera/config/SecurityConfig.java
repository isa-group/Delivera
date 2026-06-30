package com.delivera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(SWAGGER_PATHS).permitAll();
                auth.requestMatchers(api + "/auth/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, api + "/organizations/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, api + "/activity-types", api + "/activity-types/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, api + "/app-config/**").permitAll();
                auth.requestMatchers(api + "/orders/public/**").permitAll();

                auth.requestMatchers(api + "/external/**").hasRole("API_KEY");
                auth.requestMatchers(api + "/admin/**").hasRole("GLOBAL_ADMIN");
                auth.requestMatchers(api + "/settings/**").hasRole(ADMIN);

                auth.requestMatchers(HttpMethod.GET, api + "/units/external").hasAnyRole(ADMIN, ANALYST);
                auth.requestMatchers(HttpMethod.GET, api + "/units/external-companies").hasAnyRole(ADMIN, ANALYST);
                auth.requestMatchers(HttpMethod.POST, api + "/units").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.POST, api + "/units/*/workers/*").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.PUT, api + UNITS_ALL).hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + "/units/*/workers/*").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + UNITS_ALL).hasRole(ADMIN);

                auth.requestMatchers(HttpMethod.POST, api + "/vehicles").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.PUT, api + "/vehicles/**").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + "/vehicles/**").hasRole(ADMIN);

                auth.requestMatchers(HttpMethod.POST, api + "/orders").hasAnyRole(ADMIN, ANALYST);
                auth.requestMatchers(HttpMethod.PATCH, api + "/orders/*/status").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(HttpMethod.DELETE, api + "/orders/**").hasRole(ADMIN);

                auth.requestMatchers(HttpMethod.POST, api + "/loyal-users").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.PUT, api + "/loyal-users/**").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.GET, api + "/loyal-users/me/orders").hasRole(LOYAL_USER);

                auth.requestMatchers(HttpMethod.POST, api + "/workers/invite").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.PATCH, api + "/workers/*/role").hasRole(ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, api + "/workers/**").hasRole(ADMIN);

                auth.requestMatchers(api + "/orders/*/messages/**").hasAnyRole(ADMIN, ANALYST, OPERATOR, LOYAL_USER);
                auth.requestMatchers(HttpMethod.POST, api + "/orders/*/messages").hasAnyRole(ADMIN, ANALYST, OPERATOR, LOYAL_USER);

                auth.requestMatchers(api + "/workers/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + UNITS_ALL).hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + "/vehicles/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + "/orders/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + "/loyal-users/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + "/fms/routing/**").hasAnyRole(ADMIN, ANALYST, OPERATOR);
                auth.requestMatchers(api + "/user/**").authenticated();
                auth.requestMatchers(api + "/activity/**").hasAnyRole(ADMIN, ANALYST);

                auth.anyRequest().denyAll();
            })
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
