package com.ridesharing.rideservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ride Service Application
 * 
 * This is the Ride Posting, Searching, and Booking Service for the Smart Ride Sharing System.
 * It handles:
 * - Ride posting by drivers
 * - Ride searching by passengers
 * - Seat booking management
 * - Ride status management
 * - Booking confirmation
 * 
 * Port: 8082
 * Database: ride_db
 * 
 * Note: Eureka Client is auto-configured when spring-cloud-starter-netflix-eureka-client 
 * dependency is present. No @EnableEurekaClient annotation needed in Spring Cloud 2023.0.0+
 * 
 * @author Smart Ride Sharing System
 * @version 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@EnableAsync
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}

