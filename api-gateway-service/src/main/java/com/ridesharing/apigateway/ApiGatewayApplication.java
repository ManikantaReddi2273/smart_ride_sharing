package com.ridesharing.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 * 
 * This is the API Gateway Service for the Smart Ride Sharing System.
 * It serves as the single entry point for all client requests.
 * 
 * Responsibilities:
 * - Request routing to appropriate microservices
 * - JWT token validation and authentication
 * - Request/Response logging
 * - Load balancing across service instances
 * - Error handling
 * 
 * Port: 8080
 * 
 * Note: Eureka Client is auto-configured when spring-cloud-starter-netflix-eureka-client 
 * dependency is present. No @EnableEurekaClient annotation needed in Spring Cloud 2023.0.0+
 * 
 * @author Smart Ride Sharing System
 * @version 1.0.0
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

