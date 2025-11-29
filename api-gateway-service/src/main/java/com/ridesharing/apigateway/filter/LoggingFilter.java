package com.ridesharing.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Logging Filter
 * Logs all incoming requests and responses for debugging and monitoring
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    
    /**
     * Filter method to log requests and responses
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().toString();
        String remoteAddress = exchange.getRequest().getRemoteAddress() != null 
            ? exchange.getRequest().getRemoteAddress().toString() 
            : "unknown";
        
        // Log incoming request
        logger.info("Incoming Request - Method: {}, Path: {}, Remote Address: {}, Time: {}", 
            method, path, remoteAddress, LocalDateTime.now());
        
        // Log response after processing
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null 
                ? exchange.getResponse().getStatusCode().value() 
                : 0;
            
            logger.info("Outgoing Response - Method: {}, Path: {}, Status: {}, Duration: {}ms", 
                method, path, statusCode, duration);
        }));
    }
    
    /**
     * Filter order (higher number = lower priority)
     * Set to lower priority to run after authentication filter
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

