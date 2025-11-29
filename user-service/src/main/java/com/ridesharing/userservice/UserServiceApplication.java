package com.ridesharing.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * User Service Application
 * 
 * This is the User Management and Authentication Service for the Smart Ride Sharing System.
 * It handles:
 * - User registration and login
 * - JWT token generation
 * - User profile management
 * - Vehicle management for drivers
 * - Role-based access control
 * 
 * Port: 8081
 * Database: user_db
 * 
 * Note: Eureka Client is auto-configured when spring-cloud-starter-netflix-eureka-client 
 * dependency is present. No @EnableEurekaClient annotation needed in Spring Cloud 2023.0.0+
 * 
 * @author Smart Ride Sharing System
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

