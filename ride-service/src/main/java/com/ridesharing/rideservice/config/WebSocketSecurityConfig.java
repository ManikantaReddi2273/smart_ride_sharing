package com.ridesharing.rideservice.config;

import org.springframework.context.annotation.Configuration;

/**
 * WebSocket Security Configuration
 * Note: WebSocket authentication is handled at the application level
 * when sending notifications. The WebSocket connection itself is allowed
 * for all authenticated users (JWT validation happens at API Gateway level
 * for HTTP requests, and we validate user context when sending notifications).
 */
@Configuration
public class WebSocketSecurityConfig {
    // WebSocket security is handled in the service layer
    // when notifications are sent, we validate the user context
}
