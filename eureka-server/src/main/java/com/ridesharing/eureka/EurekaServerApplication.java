package com.ridesharing.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server Application
 * 
 * This is the Service Discovery and Registration Server for the Smart Ride Sharing System.
 * All microservices will register themselves with this Eureka Server.
 * 
 * Port: 8761
 * 
 * @author Smart Ride Sharing System
 * @version 1.0.0
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}

