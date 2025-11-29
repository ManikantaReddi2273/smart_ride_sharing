package com.ridesharing.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized CORS Configuration for API Gateway
 * Handles all CORS for the entire microservices architecture
 * Prevents duplicate CORS headers by being the single source of truth
 * 
 * This is the ONLY place where CORS should be configured.
 * All other services should NOT have CORS configuration.
 */
@Configuration
public class SecurityConfig {
    
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;
    
    /**
     * Centralized CORS Filter - Single source of CORS configuration
     * This is the ONLY place where CORS should be configured
     * All other services should NOT have CORS configuration
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Parse allowed origins from configuration (comma-separated)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        corsConfig.setAllowedOrigins(origins.stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
        
        // Allow all required HTTP methods
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allow all headers including Authorization
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // Expose headers that frontend might need
        corsConfig.setExposedHeaders(Arrays.asList(
            "Authorization", "X-User-Id", "X-User-Email", "X-User-Role"
        ));
        
        // Set max age for preflight requests (24 hours)
        corsConfig.setMaxAge(86400L);
        
        // Apply to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsWebFilter(source);
    }
}

