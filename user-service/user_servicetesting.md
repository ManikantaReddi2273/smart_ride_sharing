# User Service API Testing Documentation

## Table of Contents
1. [Overview](#overview)
2. [Base Configuration](#base-configuration)
3. [API Endpoints](#api-endpoints)
4. [Testing Scenarios](#testing-scenarios)
5. [Postman Collection](#postman-collection)
6. [cURL Commands](#curl-commands)
7. [Error Scenarios](#error-scenarios)
8. [Test Data](#test-data)

---

## Overview

This document provides comprehensive testing documentation for all User Service APIs. The User Service handles user registration, authentication, profile management, and vehicle management for drivers.

**Service Details:**
- **Base URL**: `http://localhost:8081`
- **Service Name**: `user-service`
- **Port**: `8081`
- **Database**: `user_db`

---

## Base Configuration

### Prerequisites
- User Service running on port 8081
- MySQL database `user_db` created and accessible
- Eureka Server running on port 8761 (optional for testing)

### Environment Variables
```
BASE_URL=http://localhost:8081
TOKEN=<will_be_set_after_login_or_register>
```

---

## API Endpoints

### 1. Register User (Public Endpoint)

**Endpoint:** `POST /api/users/register`

**URL:** `http://localhost:8081/api/users/register`

**Authentication:** Not Required

**Headers:**
```
Content-Type: application/json
```

#### Request Body - Register Driver

```json
{
  "email": "driver@example.com",
  "phone": "+1234567890",
  "password": "password123",
  "name": "John Driver",
  "role": "DRIVER"
}
```

#### Request Body - Register Passenger

```json
{
  "email": "passenger@example.com",
  "phone": "+1234567891",
  "password": "password123",
  "name": "Jane Passenger",
  "role": "PASSENGER"
}
```

#### Success Response (201 Created)

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoiZHJpdmVyQGV4YW1wbGUuY29tIiwicm9sZSI6IkRSSVZFUiIsInN1YiI6ImRyaXZlckBleGFtcGxlLmNvbSIsImlhdCI6MTcwNDE2MDAwMCwiZXhwIjoxNzA0MjQ2NDAwfQ.example",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "driver@example.com",
  "name": "John Driver",
  "role": "DRIVER",
  "expiresIn": 86400000
}
```

#### Error Response - Validation Error (400 Bad Request)

```json
{
  "error": "Validation Failed",
  "message": "{email=Email must be valid, password=Password must be between 8 and 100 characters}",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - User Already Exists (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "User already exists with this email or phone number",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Valid Driver Registration | Valid driver data | 201 Created with token |
| Valid Passenger Registration | Valid passenger data | 201 Created with token |
| Invalid Email Format | Invalid email | 400 Validation Error |
| Short Password | Password < 8 chars | 400 Validation Error |
| Duplicate Email | Existing email | 400 User Already Exists |
| Duplicate Phone | Existing phone | 400 User Already Exists |
| Missing Required Fields | Missing name/email | 400 Validation Error |
| Invalid Role | Role other than DRIVER/PASSENGER | 400 Validation Error |

---

### 2. Login User (Public Endpoint)

**Endpoint:** `POST /api/users/login`

**URL:** `http://localhost:8081/api/users/login`

**Authentication:** Not Required

**Headers:**
```
Content-Type: application/json
```

#### Request Body - Login with Email

```json
{
  "emailOrPhone": "driver@example.com",
  "password": "password123"
}
```

#### Request Body - Login with Phone

```json
{
  "emailOrPhone": "+1234567890",
  "password": "password123"
}
```

#### Success Response (200 OK)

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoiZHJpdmVyQGV4YW1wbGUuY29tIiwicm9sZSI6IkRSSVZFUiIsInN1YiI6ImRyaXZlckBleGFtcGxlLmNvbSIsImlhdCI6MTcwNDE2MDAwMCwiZXhwIjoxNzA0MjQ2NDAwfQ.example",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "driver@example.com",
  "name": "John Driver",
  "role": "DRIVER",
  "expiresIn": 86400000
}
```

#### Error Response - Invalid Credentials (401 Unauthorized)

```json
{
  "error": "Unauthorized",
  "message": "Invalid email/phone or password",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - Account Not Active (401 Unauthorized)

```json
{
  "error": "Unauthorized",
  "message": "User account is not active",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Valid Login with Email | Correct email/password | 200 OK with token |
| Valid Login with Phone | Correct phone/password | 200 OK with token |
| Wrong Password | Correct email, wrong password | 401 Unauthorized |
| Non-existent User | Email/phone not registered | 401 Unauthorized |
| Missing Credentials | Empty emailOrPhone/password | 400 Validation Error |
| Inactive Account | Login with inactive account | 401 Account Not Active |

---

### 3. Get User Profile (Protected Endpoint)

**Endpoint:** `GET /api/users/profile`

**URL:** `http://localhost:8081/api/users/profile`

**Authentication:** Required (JWT Token)

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

#### Request Body
None

#### Success Response - Driver Profile (200 OK)

```json
{
  "id": 1,
  "email": "driver@example.com",
  "phone": "+1234567890",
  "name": "John Driver",
  "role": "DRIVER",
  "status": "ACTIVE",
  "driverProfile": {
    "id": 1,
    "licenseNumber": null,
    "licenseExpiryDate": null,
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
  "passengerProfile": null,
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

#### Success Response - Passenger Profile (200 OK)

```json
{
  "id": 2,
  "email": "passenger@example.com",
  "phone": "+1234567891",
  "name": "Jane Passenger",
  "role": "PASSENGER",
  "status": "ACTIVE",
  "driverProfile": null,
  "vehicles": null,
  "passengerProfile": {
    "id": 1,
    "preferences": null
  },
  "createdAt": "2024-01-01T10:05:00",
  "updatedAt": "2024-01-01T10:05:00"
}
```

#### Error Response - Missing Token (401 Unauthorized)

```json
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - Invalid Token (401 Unauthorized)

```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token",
  "status": 401,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Valid Token - Driver | Valid JWT token | 200 OK with driver profile |
| Valid Token - Passenger | Valid JWT token | 200 OK with passenger profile |
| Missing Token | No Authorization header | 401 Unauthorized |
| Invalid Token | Invalid JWT token | 401 Unauthorized |
| Expired Token | Expired JWT token | 401 Unauthorized |
| Malformed Token | Token without "Bearer " prefix | 401 Unauthorized |

---

### 4. Update User Profile (Protected Endpoint)

**Endpoint:** `PUT /api/users/profile`

**URL:** `http://localhost:8081/api/users/profile`

**Authentication:** Required (JWT Token)

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

#### Request Body - Full Update

```json
{
  "name": "John Updated",
  "phone": "+1234567892",
  "email": "newemail@example.com"
}
```

#### Request Body - Partial Update (Name Only)

```json
{
  "name": "John Updated Name"
}
```

#### Request Body - Partial Update (Phone Only)

```json
{
  "phone": "+1234567893"
}
```

#### Success Response (200 OK)

```json
{
  "id": 1,
  "email": "newemail@example.com",
  "phone": "+1234567892",
  "name": "John Updated",
  "role": "DRIVER",
  "status": "ACTIVE",
  "driverProfile": {
    "id": 1,
    "licenseNumber": null,
    "licenseExpiryDate": null,
    "isVerified": false
  },
  "vehicles": [],
  "passengerProfile": null,
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T12:00:00"
}
```

#### Error Response - Email Already Exists (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "Email already exists",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - Phone Already Exists (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "Phone number already exists",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - Invalid Email Format (400 Bad Request)

```json
{
  "error": "Validation Failed",
  "message": "{email=Email must be valid}",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Update Name Only | Valid name | 200 OK with updated name |
| Update Email Only | Valid new email | 200 OK with updated email |
| Update Phone Only | Valid new phone | 200 OK with updated phone |
| Update All Fields | Valid name/email/phone | 200 OK with all updates |
| Duplicate Email | Email already exists | 400 Bad Request |
| Duplicate Phone | Phone already exists | 400 Bad Request |
| Invalid Email Format | Invalid email | 400 Validation Error |
| Invalid Phone Format | Invalid phone | 400 Validation Error |
| Missing Token | No Authorization header | 401 Unauthorized |

---

### 5. Add Vehicle (Protected Endpoint - Driver Only)

**Endpoint:** `POST /api/users/vehicles`

**URL:** `http://localhost:8081/api/users/vehicles`

**Authentication:** Required (JWT Token)

**Authorization:** DRIVER role required

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

#### Request Body - Full Vehicle Details

```json
{
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}
```

#### Request Body - Minimal Vehicle Details

```json
{
  "model": "Honda Civic",
  "licensePlate": "XYZ789",
  "capacity": 5
}
```

#### Success Response (201 Created)

```json
{
  "id": 1,
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020,
  "isVerified": false,
  "createdAt": "2024-01-01T12:00:00",
  "updatedAt": "2024-01-01T12:00:00"
}
```

#### Error Response - Not a Driver (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "Only drivers can add vehicles",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - License Plate Exists (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "Vehicle with this license plate already exists",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Error Response - Validation Error (400 Bad Request)

```json
{
  "error": "Validation Failed",
  "message": "{capacity=Capacity must be at least 2, model=Vehicle model is required}",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Valid Vehicle - Driver | Valid vehicle data with driver token | 201 Created |
| Valid Vehicle - Minimal Data | Only required fields | 201 Created |
| Passenger Trying to Add | Vehicle data with passenger token | 400 Not a Driver |
| Duplicate License Plate | Existing license plate | 400 Already Exists |
| Missing Required Fields | Missing model/licensePlate | 400 Validation Error |
| Invalid Capacity | Capacity < 2 | 400 Validation Error |
| Missing Token | No Authorization header | 401 Unauthorized |

---

### 6. Get User Vehicles (Protected Endpoint - Driver Only)

**Endpoint:** `GET /api/users/vehicles`

**URL:** `http://localhost:8081/api/users/vehicles`

**Authentication:** Required (JWT Token)

**Authorization:** DRIVER role required

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

#### Request Body
None

#### Success Response - With Vehicles (200 OK)

```json
[
  {
    "id": 1,
    "model": "Toyota Camry",
    "licensePlate": "ABC123",
    "color": "Blue",
    "capacity": 4,
    "year": 2020,
    "isVerified": false,
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T12:00:00"
  },
  {
    "id": 2,
    "model": "Honda Civic",
    "licensePlate": "XYZ789",
    "color": "Red",
    "capacity": 5,
    "year": 2021,
    "isVerified": false,
    "createdAt": "2024-01-01T12:30:00",
    "updatedAt": "2024-01-01T12:30:00"
  }
]
```

#### Success Response - No Vehicles (200 OK)

```json
[]
```

#### Error Response - Not a Driver (400 Bad Request)

```json
{
  "error": "Bad Request",
  "message": "Only drivers can have vehicles",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

#### Test Cases

| Test Case | Input | Expected Result |
|-----------|-------|-----------------|
| Driver with Vehicles | Valid driver token with vehicles | 200 OK with vehicle list |
| Driver without Vehicles | Valid driver token, no vehicles | 200 OK with empty array |
| Passenger Request | Passenger token | 400 Not a Driver |
| Missing Token | No Authorization header | 401 Unauthorized |

---

## Testing Scenarios

### Scenario 1: Complete Driver Registration and Vehicle Management Flow

**Step 1: Register Driver**
```bash
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "driver1@test.com",
  "phone": "+1111111111",
  "password": "driver123",
  "name": "Driver One",
  "role": "DRIVER"
}
```
**Expected:** 201 Created with JWT token  
**Save:** `token` from response

**Step 2: Get Profile**
```bash
GET http://localhost:8081/api/users/profile
Authorization: Bearer <token_from_step_1>
```
**Expected:** 200 OK with driver profile (no vehicles yet)

**Step 3: Add First Vehicle**
```bash
POST http://localhost:8081/api/users/vehicles
Authorization: Bearer <token_from_step_1>
Content-Type: application/json

{
  "model": "Toyota Camry",
  "licensePlate": "DRV001",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}
```
**Expected:** 201 Created with vehicle details

**Step 4: Add Second Vehicle**
```bash
POST http://localhost:8081/api/users/vehicles
Authorization: Bearer <token_from_step_1>
Content-Type: application/json

{
  "model": "Honda Civic",
  "licensePlate": "DRV002",
  "color": "Red",
  "capacity": 5,
  "year": 2021
}
```
**Expected:** 201 Created with vehicle details

**Step 5: Get All Vehicles**
```bash
GET http://localhost:8081/api/users/vehicles
Authorization: Bearer <token_from_step_1>
```
**Expected:** 200 OK with array of 2 vehicles

**Step 6: Update Profile**
```bash
PUT http://localhost:8081/api/users/profile
Authorization: Bearer <token_from_step_1>
Content-Type: application/json

{
  "name": "Driver One Updated"
}
```
**Expected:** 200 OK with updated profile

**Step 7: Verify Profile Update**
```bash
GET http://localhost:8081/api/users/profile
Authorization: Bearer <token_from_step_1>
```
**Expected:** 200 OK with updated name

---

### Scenario 2: Complete Passenger Registration Flow

**Step 1: Register Passenger**
```bash
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "passenger1@test.com",
  "phone": "+2222222222",
  "password": "passenger123",
  "name": "Passenger One",
  "role": "PASSENGER"
}
```
**Expected:** 201 Created with JWT token  
**Save:** `token` from response

**Step 2: Login**
```bash
POST http://localhost:8081/api/users/login
Content-Type: application/json

{
  "emailOrPhone": "passenger1@test.com",
  "password": "passenger123"
}
```
**Expected:** 200 OK with JWT token

**Step 3: Get Profile**
```bash
GET http://localhost:8081/api/users/profile
Authorization: Bearer <token_from_step_2>
```
**Expected:** 200 OK with passenger profile (no vehicles)

**Step 4: Try to Add Vehicle (Should Fail)**
```bash
POST http://localhost:8081/api/users/vehicles
Authorization: Bearer <token_from_step_2>
Content-Type: application/json

{
  "model": "Toyota Camry",
  "licensePlate": "PAS001",
  "capacity": 4
}
```
**Expected:** 400 Bad Request - "Only drivers can add vehicles"

---

### Scenario 3: Error Handling and Validation

**Test 1: Register with Invalid Email**
```bash
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "invalid-email",
  "password": "password123",
  "name": "Test User",
  "role": "DRIVER"
}
```
**Expected:** 400 Validation Error

**Test 2: Register with Short Password**
```bash
POST http://localhost:8081/api/users/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "short",
  "name": "Test User",
  "role": "DRIVER"
}
```
**Expected:** 400 Validation Error

**Test 3: Login with Wrong Password**
```bash
POST http://localhost:8081/api/users/login
Content-Type: application/json

{
  "emailOrPhone": "driver1@test.com",
  "password": "wrongpassword"
}
```
**Expected:** 401 Unauthorized

**Test 4: Access Protected Endpoint Without Token**
```bash
GET http://localhost:8081/api/users/profile
```
**Expected:** 401 Unauthorized

**Test 5: Add Vehicle with Duplicate License Plate**
```bash
POST http://localhost:8081/api/users/vehicles
Authorization: Bearer <driver_token>
Content-Type: application/json

{
  "model": "Different Model",
  "licensePlate": "DRV001",
  "capacity": 4
}
```
**Expected:** 400 Bad Request - "Vehicle with this license plate already exists"

---

## Postman Collection

### Collection Setup

**Collection Variables:**
```
base_url: http://localhost:8081
token: (will be set automatically)
userId: (will be set automatically)
```

### Request 1: Register Driver

**Method:** `POST`  
**URL:** `{{base_url}}/api/users/register`  
**Headers:**
```
Content-Type: application/json
```
**Body (raw JSON):**
```json
{
  "email": "driver@test.com",
  "phone": "+1234567890",
  "password": "password123",
  "name": "Test Driver",
  "role": "DRIVER"
}
```
**Tests (Postman Script):**
```javascript
if (pm.response.code === 201) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set("token", jsonData.token);
    pm.collectionVariables.set("userId", jsonData.userId);
    console.log("Token saved: " + jsonData.token);
}
```

### Request 2: Register Passenger

**Method:** `POST`  
**URL:** `{{base_url}}/api/users/register`  
**Headers:**
```
Content-Type: application/json
```
**Body (raw JSON):**
```json
{
  "email": "passenger@test.com",
  "phone": "+1234567891",
  "password": "password123",
  "name": "Test Passenger",
  "role": "PASSENGER"
}
```
**Tests (Postman Script):**
```javascript
if (pm.response.code === 201) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set("token", jsonData.token);
    pm.collectionVariables.set("userId", jsonData.userId);
}
```

### Request 3: Login

**Method:** `POST`  
**URL:** `{{base_url}}/api/users/login`  
**Headers:**
```
Content-Type: application/json
```
**Body (raw JSON):**
```json
{
  "emailOrPhone": "driver@test.com",
  "password": "password123"
}
```
**Tests (Postman Script):**
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set("token", jsonData.token);
    pm.collectionVariables.set("userId", jsonData.userId);
}
```

### Request 4: Get Profile

**Method:** `GET`  
**URL:** `{{base_url}}/api/users/profile`  
**Headers:**
```
Authorization: Bearer {{token}}
```

### Request 5: Update Profile

**Method:** `PUT`  
**URL:** `{{base_url}}/api/users/profile`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```
**Body (raw JSON):**
```json
{
  "name": "Updated Name"
}
```

### Request 6: Add Vehicle

**Method:** `POST`  
**URL:** `{{base_url}}/api/users/vehicles`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```
**Body (raw JSON):**
```json
{
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}
```

### Request 7: Get Vehicles

**Method:** `GET`  
**URL:** `{{base_url}}/api/users/vehicles`  
**Headers:**
```
Authorization: Bearer {{token}}
```

---

## cURL Commands

### 1. Register Driver

```bash
curl -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "driver@test.com",
    "phone": "+1234567890",
    "password": "password123",
    "name": "Test Driver",
    "role": "DRIVER"
  }'
```

### 2. Register Passenger

```bash
curl -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "passenger@test.com",
    "phone": "+1234567891",
    "password": "password123",
    "name": "Test Passenger",
    "role": "PASSENGER"
  }'
```

### 3. Login

```bash
curl -X POST http://localhost:8081/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "emailOrPhone": "driver@test.com",
    "password": "password123"
  }'
```

### 4. Get Profile (Replace TOKEN with actual token)

```bash
curl -X GET http://localhost:8081/api/users/profile \
  -H "Authorization: Bearer TOKEN"
```

### 5. Update Profile (Replace TOKEN with actual token)

```bash
curl -X PUT http://localhost:8081/api/users/profile \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Name"
  }'
```

### 6. Add Vehicle (Replace TOKEN with actual token)

```bash
curl -X POST http://localhost:8081/api/users/vehicles \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Toyota Camry",
    "licensePlate": "ABC123",
    "color": "Blue",
    "capacity": 4,
    "year": 2020
  }'
```

### 7. Get Vehicles (Replace TOKEN with actual token)

```bash
curl -X GET http://localhost:8081/api/users/vehicles \
  -H "Authorization: Bearer TOKEN"
```

---

## Error Scenarios

### Common Error Responses

#### 400 Bad Request - Validation Errors
- Invalid email format
- Password too short (< 8 characters)
- Missing required fields
- Invalid role (not DRIVER or PASSENGER)
- Invalid phone format
- Capacity < 2 for vehicles

#### 400 Bad Request - Business Logic Errors
- User already exists (email/phone)
- Email already exists (update profile)
- Phone already exists (update profile)
- License plate already exists
- Only drivers can add vehicles
- Only drivers can have vehicles

#### 401 Unauthorized - Authentication Errors
- Missing Authorization header
- Invalid JWT token
- Expired JWT token
- Malformed token (missing "Bearer " prefix)
- Invalid credentials (login)
- Account not active

#### 404 Not Found
- User not found (if implemented)

---

## Test Data

### Test Users

#### Driver 1
```json
{
  "email": "driver1@test.com",
  "phone": "+1111111111",
  "password": "driver123",
  "name": "Driver One",
  "role": "DRIVER"
}
```

#### Driver 2
```json
{
  "email": "driver2@test.com",
  "phone": "+1111111112",
  "password": "driver123",
  "name": "Driver Two",
  "role": "DRIVER"
}
```

#### Passenger 1
```json
{
  "email": "passenger1@test.com",
  "phone": "+2222222222",
  "password": "passenger123",
  "name": "Passenger One",
  "role": "PASSENGER"
}
```

#### Passenger 2
```json
{
  "email": "passenger2@test.com",
  "phone": "+2222222223",
  "password": "passenger123",
  "name": "Passenger Two",
  "role": "PASSENGER"
}
```

### Test Vehicles

#### Vehicle 1
```json
{
  "model": "Toyota Camry",
  "licensePlate": "ABC123",
  "color": "Blue",
  "capacity": 4,
  "year": 2020
}
```

#### Vehicle 2
```json
{
  "model": "Honda Civic",
  "licensePlate": "XYZ789",
  "color": "Red",
  "capacity": 5,
  "year": 2021
}
```

#### Vehicle 3
```json
{
  "model": "Ford Mustang",
  "licensePlate": "MUS456",
  "color": "Black",
  "capacity": 4,
  "year": 2022
}
```

---

## Testing Checklist

### Registration & Authentication
- [ ] Register driver successfully
- [ ] Register passenger successfully
- [ ] Register with invalid email format
- [ ] Register with short password
- [ ] Register with duplicate email
- [ ] Register with duplicate phone
- [ ] Login with email successfully
- [ ] Login with phone successfully
- [ ] Login with wrong password
- [ ] Login with non-existent user

### Profile Management
- [ ] Get profile with valid token
- [ ] Get profile without token
- [ ] Get profile with invalid token
- [ ] Update profile name
- [ ] Update profile email
- [ ] Update profile phone
- [ ] Update profile with duplicate email
- [ ] Update profile with duplicate phone
- [ ] Update profile with invalid email format

### Vehicle Management (Driver Only)
- [ ] Add vehicle successfully
- [ ] Add vehicle with minimal data
- [ ] Add vehicle with duplicate license plate
- [ ] Add vehicle as passenger (should fail)
- [ ] Get vehicles successfully
- [ ] Get vehicles as passenger (should fail)
- [ ] Get vehicles when no vehicles exist

### Error Handling
- [ ] All validation errors return 400
- [ ] All authentication errors return 401
- [ ] All authorization errors return 400
- [ ] Error responses include proper structure
- [ ] Error messages are clear and helpful

---

## Notes

1. **JWT Token Expiration**: Tokens expire after 24 hours (86400000 ms). You'll need to login again after expiration.

2. **Password Requirements**: Minimum 8 characters, maximum 100 characters.

3. **Email Validation**: Must be a valid email format.

4. **Phone Validation**: Must match pattern `^[+]?[0-9]{10,15}$`.

5. **Role Values**: Only "DRIVER" or "PASSENGER" are accepted (case-sensitive).

6. **Vehicle Capacity**: Must be at least 2 (driver + at least 1 passenger).

7. **License Plate**: Must be unique across all vehicles.

8. **Token Format**: Always include "Bearer " prefix in Authorization header.

9. **Database**: Ensure `user_db` database exists before testing.

10. **Service Status**: Verify User Service is running on port 8081 before testing.

---

## Troubleshooting

### Issue: 401 Unauthorized on all protected endpoints
**Solution:** Ensure you're including the Authorization header with "Bearer " prefix and a valid token.

### Issue: 400 Bad Request on registration
**Solution:** Check that all required fields are present and meet validation requirements (email format, password length, etc.).

### Issue: Cannot connect to service
**Solution:** Verify User Service is running on port 8081 and check application.properties for correct configuration.

### Issue: Database connection errors
**Solution:** Ensure MySQL is running and `user_db` database exists. Check database credentials in application.properties.

---

**Document Version:** 1.0.0  
**Last Updated:** 2024-01-01  
**Service Version:** 1.0.0

