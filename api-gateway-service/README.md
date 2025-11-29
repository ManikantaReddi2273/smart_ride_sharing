# API Gateway Service - Smart Ride Sharing System

## Overview
This is the API Gateway Service for the Smart Ride Sharing System. It serves as the single entry point for all client requests, routing them to appropriate microservices.

## Port
- **Port**: 8080
- **Base URL**: http://localhost:8080

## Features
- Request routing to microservices via Eureka service discovery
- JWT token validation for protected endpoints
- Request/Response logging
- Load balancing across service instances
- Error handling and standardized error responses
- Public endpoint handling (register, login)

## Service Routes

### Current Routes (Active)
- `/api/users/**` → User Service (port 8081)

### Future Routes (Commented - will be enabled when services are implemented)
- `/api/rides/**` → Ride Service (port 8082)
- `/api/payments/**` → Payment Service (port 8084)
- `/api/routes/**` → Route Service (port 8083)
- `/api/notifications/**` → Notification Service (port 8085)
- `/api/reviews/**` → Review Service (port 8086)
- `/api/admin/**` → Admin Service (port 8087)

## Public Endpoints (No Authentication Required)
- `POST /api/users/register` - User registration
- `POST /api/users/login` - User login

## Protected Endpoints (JWT Token Required)
All other endpoints require a valid JWT token in the Authorization header:
```
Authorization: Bearer <token>
```

## How It Works

### Request Flow
```
1. Client Request → API Gateway (port 8080)
2. API Gateway validates JWT token (if required)
3. API Gateway routes request to appropriate service via Eureka
4. Service processes request and returns response
5. API Gateway logs response and returns to client
```

### JWT Token Validation
- Tokens are validated using the same secret key as User Service
- Valid tokens are allowed through, user info is added to headers
- Invalid/expired tokens return 401 Unauthorized
- Public endpoints bypass token validation

### Service Discovery
- Uses Eureka Server for service discovery
- Automatically finds service instances
- Load balances across multiple instances if available

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Eureka Server running on port 8761
- User Service running on port 8081 (for testing)

### Build and Run
```bash
# Navigate to api-gateway-service directory
cd api-gateway-service

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Verify Service Registration
Once the service is running, check Eureka Dashboard at:
- http://localhost:8761
- Look for `api-gateway-service` in the registered services

## Testing the Gateway

### Test 1: Register User (Public Endpoint)
```bash
POST http://localhost:8080/api/users/register
Content-Type: application/json

{
  "email": "test@example.com",
  "phone": "+1234567890",
  "password": "password123",
  "name": "Test User",
  "role": "DRIVER"
}
```

### Test 2: Login (Public Endpoint)
```bash
POST http://localhost:8080/api/users/login
Content-Type: application/json

{
  "emailOrPhone": "test@example.com",
  "password": "password123"
}
```
Save the token from the response.

### Test 3: Get Profile (Protected Endpoint)
```bash
GET http://localhost:8080/api/users/profile
Authorization: Bearer <token_from_login>
```

### Test 4: Test Without Token (Should Fail)
```bash
GET http://localhost:8080/api/users/profile
```
Expected: 401 Unauthorized

### Test 5: Test with Invalid Token (Should Fail)
```bash
GET http://localhost:8080/api/users/profile
Authorization: Bearer invalid_token_here
```
Expected: 401 Unauthorized

## Configuration

### JWT Configuration
```properties
jwt.secret=SmartRideSharingSystemSecretKeyForJWTTokenGeneration2024
```
**Important**: This must match the secret key in User Service's `application.properties`.

### Eureka Configuration
```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

### Gateway Discovery
```properties
spring.cloud.gateway.discovery.locator.enabled=true
spring.cloud.gateway.discovery.locator.lower-case-service-id=true
```

## Request Headers Added by Gateway

For authenticated requests, the gateway adds the following headers to downstream services:
- `X-User-Id`: User ID from JWT token
- `X-User-Email`: User email from JWT token
- `X-User-Role`: User role from JWT token

These headers can be used by downstream services to identify the authenticated user.

## Error Responses

### 401 Unauthorized - Missing Token
```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid token",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

### 401 Unauthorized - Invalid Token
```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

## Logging

The gateway logs all requests and responses:
- Incoming requests: Method, Path, Remote Address, Time
- Outgoing responses: Method, Path, Status Code, Duration

Example log output:
```
INFO - Incoming Request - Method: GET, Path: /api/users/profile, Remote Address: /127.0.0.1:54321, Time: 2024-01-01T12:00:00
INFO - Outgoing Response - Method: GET, Path: /api/users/profile, Status: 200, Duration: 45ms
```

## Health Check
- Health endpoint: http://localhost:8080/actuator/health
- Info endpoint: http://localhost:8080/actuator/info
- Gateway routes: http://localhost:8080/actuator/gateway/routes

## Notes

1. **JWT Secret**: Must match User Service's JWT secret for token validation to work.

2. **Service Discovery**: Gateway uses Eureka to discover services. Ensure services are registered with Eureka.

3. **Load Balancing**: If multiple instances of a service are running, Gateway automatically load balances requests.

4. **Public Endpoints**: Register and login endpoints don't require authentication.

5. **CORS**: CORS configuration can be added later when frontend is ready.

6. **Rate Limiting**: Can be added in future iterations if needed.

## Troubleshooting

### Issue: 503 Service Unavailable
**Solution**: Check if the target service (e.g., User Service) is running and registered with Eureka.

### Issue: 401 Unauthorized on valid token
**Solution**: Verify JWT secret matches between API Gateway and User Service.

### Issue: Cannot connect to service
**Solution**: Verify API Gateway is running on port 8080 and Eureka Server is running on port 8761.

### Issue: Routes not working
**Solution**: Check service names in Eureka match the route configuration (case-sensitive, lowercase).

---

**Document Version:** 1.0.0  
**Last Updated:** 2024-01-01  
**Service Version:** 1.0.0

