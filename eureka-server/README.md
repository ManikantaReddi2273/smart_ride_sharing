# Eureka Server - Smart Ride Sharing System

## Overview
This is the Service Discovery and Registration Server for the Smart Ride Sharing System. All microservices will register themselves with this Eureka Server, allowing them to discover and communicate with each other.

## Port
- **Port**: 8761
- **Dashboard URL**: http://localhost:8761

## Features
- Service registration and discovery
- Health monitoring of registered services
- Load balancing information
- Service instance management
- Self-preservation mode enabled

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build and Run
```bash
# Navigate to eureka-server directory
cd eureka-server

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Access Eureka Dashboard
Once the server is running, access the Eureka Dashboard at:
- http://localhost:8761

## Configuration
The server is configured via `application.properties`:
- Self-registration is disabled (Eureka Server doesn't register itself)
- Self-preservation mode is enabled
- Response cache is configured for performance

## Services That Will Register
- User Service (port 8081)
- Ride Service (port 8082)
- Route Service (port 8083)
- Payment Service (port 8084)
- Notification Service (port 8085)
- Review Service (port 8086)
- Admin Service (port 8087)
- API Gateway Service (port 8080)

