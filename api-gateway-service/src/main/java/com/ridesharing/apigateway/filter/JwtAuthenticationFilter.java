package com.ridesharing.apigateway.filter;

import com.ridesharing.apigateway.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * JWT Authentication Filter
 * Validates JWT tokens for protected endpoints
 * Allows public endpoints (register, login) without authentication
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * List of public endpoints that don't require authentication
     */
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
        "/api/users/register",
        "/api/users/login",
        "/api/users/verify-otp"
    );
    
    /**
     * Filter method to validate JWT tokens
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // Check if the path is a public endpoint
        if (isPublicEndpoint(path, exchange)) {
            logger.debug("Public endpoint accessed: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract token from Authorization header
        String token = getTokenFromRequest(request);
        
        // Validate token
        if (!StringUtils.hasText(token)) {
            logger.warn("Missing JWT token for path: {}", path);
            return onError(exchange, "Missing or invalid token", HttpStatus.UNAUTHORIZED);
        }
        
        if (!jwtUtil.validateToken(token)) {
            logger.warn("Invalid JWT token for path: {}", path);
            return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        
        // Token is valid, add user information to headers for downstream services
        try {
            Long userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            
            // Add user information to request headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .build();
            
            logger.debug("JWT token validated successfully for user: {} (ID: {})", email, userId);
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            logger.error("Error processing JWT token: {}", e.getMessage());
            return onError(exchange, "Error processing token", HttpStatus.UNAUTHORIZED);
        }
    }
    
    /**
     * Check if the path is a public endpoint
     */
    private boolean isPublicEndpoint(String path, ServerWebExchange exchange) {
        // Check exact matches
        if (PUBLIC_ENDPOINTS.contains(path)) {
            return true;
        }
        
        // Check if path starts with public endpoint patterns
        if (path.startsWith("/api/rides/search") || 
            path.startsWith("/api/users/register") || 
            path.startsWith("/api/users/login") ||
            path.startsWith("/api/users/verify-otp")) {
            return true;
        }
        
        // GET requests to /api/rides/{id} are public (ride details)
        if (path.matches("/api/rides/\\d+") && 
            exchange.getRequest().getMethod().toString().equals("GET")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Extract JWT token from Authorization header
     * Format: "Bearer <token>"
     */
    private String getTokenFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
    
    /**
     * Handle authentication error
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorBody = String.format(
            "{\"error\":\"%s\",\"message\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
            status.getReasonPhrase(),
            message,
            status.value(),
            java.time.LocalDateTime.now()
        );
        
        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(errorBody.getBytes()))
        );
    }
    
    /**
     * Filter order (lower number = higher priority)
     * Set to high priority to run before other filters
     */
    @Override
    public int getOrder() {
        return -100;
    }
}

