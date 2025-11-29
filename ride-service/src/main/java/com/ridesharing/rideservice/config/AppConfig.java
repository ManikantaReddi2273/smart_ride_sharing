package com.ridesharing.rideservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * General application-level configuration for Ride Service.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate bean used for calling external APIs (e.g., Google Maps).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


