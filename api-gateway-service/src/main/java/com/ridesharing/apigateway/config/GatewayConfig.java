package com.ridesharing.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Configuration
 * Defines routing rules for all microservices
 * Routes requests to appropriate services based on URL patterns
 */
@Configuration
public class GatewayConfig {
    
    /**
     * Configure routes for all microservices
     * Uses service discovery (Eureka) to find service instances
     * 
     * Route Patterns:
     * - /api/users/** → User Service (port 8081)
     * - /api/rides/** → Ride Service (port 8082) - Future
     * - /api/payments/** → Payment Service (port 8084) - Future
     * - /api/routes/** → Route Service (port 8083) - Future
     * - /api/notifications/** → Notification Service (port 8085) - Future
     * - /api/reviews/** → Review Service (port 8086) - Future
     * - /api/admin/** → Admin Service (port 8087) - Future
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Route
                .route("user-service", r -> r
                    .path("/api/users/**")
                    .uri("lb://user-service") // lb = load balanced, uses Eureka service name
                )
                
                // Ride Service Route
                .route("ride-service", r -> r
                    .path("/api/rides/**")
                    .uri("lb://ride-service")
                )
                
                // Payment Service Route
                .route("payment-service", r -> r
                    .path("/api/payments/**")
                    .uri("lb://payment-service")
                )
                
                // Route Service Route (Future)
                // .route("route-service", r -> r
                //     .path("/api/routes/**")
                //     .uri("lb://route-service")
                // )
                
                // Notification Service Route (Future)
                // .route("notification-service", r -> r
                //     .path("/api/notifications/**")
                //     .uri("lb://notification-service")
                // )
                
                // Review Service Route (Future)
                // .route("review-service", r -> r
                //     .path("/api/reviews/**")
                //     .uri("lb://review-service")
                // )
                
                // Admin Service Route (Future)
                // .route("admin-service", r -> r
                //     .path("/api/admin/**")
                //     .uri("lb://admin-service")
                // )
                
                .build();
    }
}

