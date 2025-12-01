package com.ridesharing.rideservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket Configuration for Real-Time Notifications
 * Enables STOMP over WebSocket for real-time communication
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Configure message broker
     * Simple broker for sending messages to clients
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker to carry messages back to the client
        // on destinations prefixed with "/topic" and "/queue"
        config.enableSimpleBroker("/topic", "/queue", "/user");
        
        // Prefix for messages bound to methods annotated with @MessageMapping
        // Clients send messages to destinations prefixed with "/app"
        config.setApplicationDestinationPrefixes("/app");
        
        // Prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints
     * Clients connect to this endpoint to establish WebSocket connection
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Parse allowed origins from configuration (comma-separated)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        List<String> originPatterns = origins.stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        
        // Register "/ws" endpoint, enabling SockJS fallback options
        // SockJS allows fallback to alternative transports if WebSocket is not available
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns.toArray(new String[0]))
                .withSockJS(); // Enable SockJS fallback options
        
        // Also register without SockJS for native WebSocket clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns.toArray(new String[0]));
    }
}
