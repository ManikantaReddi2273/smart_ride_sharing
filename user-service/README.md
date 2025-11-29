# User Service - Smart Ride Sharing System

## Overview
This is the User Management and Authentication Service for the Smart Ride Sharing System. It handles user registration, login, profile management, and vehicle management for drivers.

## Port
- **Port**: 8081
- **Base URL**: http://localhost:8081

## Features
- User registration (email/phone)
- User login with JWT authentication
- Password encryption using BCrypt
- Role-based access control (DRIVER, PASSENGER, ADMIN)
- User profile management
- Vehicle management for drivers
- Service discovery with Eureka

## Database
- **Database Name**: `user_db`
- **Tables**:
  - `users` - User accounts
  - `roles` - User roles (DRIVER, PASSENGER, ADMIN)
  - `driver_profiles` - Driver-specific information
  - `passenger_profiles` - Passenger-specific information
  - `vehicles` - Vehicle details for drivers

## API Endpoints

### Public Endpoints (No Authentication Required)

#### 1. Register User
```
POST /api/users/register
Content-Type: application/json

Request Body:
{
  "email": "user@example.com",
  "phone": "+1234567890",
  "password": "password123",
  "name": "John Doe",
  "role": "DRIVER" | "PASSENGER"
}

Response: 201 Created
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "name": "John Doe",
  "role": "DRIVER",
  "expiresIn": 86400000
}
```

#### 2. Login User
```
POST /api/users/login
Content-Type: application/json

Request Body:
{
  "emailOrPhone": "user@example.com",
  "password": "password123"
}

Response: 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "name": "John Doe",
  "role": "DRIVER",
  "expiresIn": 86400000
}
```

### Protected Endpoints (Authentication Required)

#### 3. Get User Profile
```
GET /api/users/profile
Authorization: Bearer <token>

Response: 200 OK
{
  "id": 1,
  "email": "user@example.com",
  "phone": "+1234567890",
  "name": "John Doe",
  "role": "DRIVER",
  "status": "ACTIVE",
  "driverProfile": {
    "id": 1,
    "licenseNumber": "DL123456",
    "licenseExpiryDate": "2025-12-31T00:00:00",
    "isVerified": false
  },
  "vehicles": [
    {
      "id": 1,
      "model": "Toyota Camry",
      "licensePlate": "ABC123",
      "color": "Blue",
      "capacity": 4,
      "year": 2020,
      "isVerified": false
    }
  ],
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

#### 4. Update User Profile
```
PUT /api/users/profile
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "name": "John Updated",
  "phone": "+1234567891",
  "email": "newemail@example.com"
}

Response: 200 OK
{
  // Updated user profile
}
```

#### 5. Add Vehicle (Driver Only)
```
POST /api/users/vehicles
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}

Response: 201 Created
{
  "id": 1,
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020,
  "isVerified": false,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

#### 6. Get User Vehicles (Driver Only)
```
GET /api/users/vehicles
Authorization: Bearer <token>

Response: 200 OK
[
  {
    "id": 1,
    "model": "Toyota Camry",
    "licensePlate": "ABC123",
    "color": "Blue",
    "capacity": 4,
    "year": 2020,
    "isVerified": false,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  }
]
```

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Eureka Server running on port 8761

### Database Setup
1. Create MySQL database:
```sql
CREATE DATABASE user_db;
```

2. Update `application.properties` with your MySQL credentials:
```properties
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Build and Run
```bash
# Navigate to user-service directory
cd user-service

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Verify Service Registration
Once the service is running, check Eureka Dashboard at:
- http://localhost:8761
- Look for `user-service` in the registered services

## Security

### Password Encryption
- Passwords are encrypted using BCrypt with strength 12
- Passwords are never stored in plain text

### JWT Authentication
- JWT tokens are generated on successful login/registration
- Token expiration: 24 hours (configurable)
- Token format: `Bearer <token>`
- Include token in `Authorization` header for protected endpoints

### Role-Based Access Control
- **DRIVER**: Can add/manage vehicles, post rides
- **PASSENGER**: Can book rides
- **ADMIN**: Can manage system (future implementation)

## Testing with Postman

### 1. Register a Driver
```json
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "driver@example.com",
  "phone": "+1234567890",
  "password": "password123",
  "name": "John Driver",
  "role": "DRIVER"
}
```

### 2. Register a Passenger
```json
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "passenger@example.com",
  "phone": "+1234567891",
  "password": "password123",
  "name": "Jane Passenger",
  "role": "PASSENGER"
}
```

### 3. Login
```json
POST http://localhost:8081/api/users/login
Content-Type: application/json

{
  "emailOrPhone": "driver@example.com",
  "password": "password123"
}
```

### 4. Get Profile (Use token from login response)
```
GET http://localhost:8081/api/users/profile
Authorization: Bearer <token_from_login>
```

### 5. Add Vehicle (Driver only)
```json
POST http://localhost:8081/api/users/vehicles
Authorization: Bearer <token>
Content-Type: application/json

{
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}
```

## Configuration

### JWT Configuration
```properties
jwt.secret=SmartRideSharingSystemSecretKeyForJWTTokenGeneration2024
jwt.expiration=86400000  # 24 hours in milliseconds
```

### Eureka Configuration
```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

## Health Check
- Health endpoint: http://localhost:8081/actuator/health
- Info endpoint: http://localhost:8081/actuator/info

## Notes
- Default roles (DRIVER, PASSENGER, ADMIN) are automatically created on first startup
- User accounts are created with ACTIVE status by default
- Vehicles are created with isVerified=false by default
- Email and phone are unique identifiers (at least one must be provided)

