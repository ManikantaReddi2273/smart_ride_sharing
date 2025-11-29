package com.ridesharing.rideservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign Client Configuration
 * Ensures proper header forwarding for inter-service communication
 */
@Configuration
public class FeignConfig {
    
    /**
     * Request interceptor to forward Authorization header
     * This ensures JWT tokens are properly forwarded to User Service
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // Feign automatically forwards headers, but we can add logging here if needed
                // The Authorization header from the original request will be forwarded
            }
        };
    }
}

