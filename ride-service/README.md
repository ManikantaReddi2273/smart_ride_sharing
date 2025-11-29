# Ride Service - Smart Ride Sharing System

## Overview
This is the Ride Posting, Searching, and Booking Service for the Smart Ride Sharing System. It handles ride posting by drivers, ride searching by passengers, and seat booking management.

## Port
- **Port**: 8082
- **Base URL**: http://localhost:8082

## Features
- Ride posting by drivers (source, destination, date, time, seats)
- Ride searching with filters (source, destination, date)
- Seat booking by passengers
- Booking confirmation and seat availability updates
- Ride status management (POSTED, BOOKED, IN_PROGRESS, COMPLETED, CANCELLED)
- Ride history for drivers and passengers
- Integration with User Service via Feign Client

## Database
- **Database Name**: `ride_db`
- **Tables**:
  - `rides` - Ride postings
  - `bookings` - Seat bookings

## API Endpoints

### 1. Post a Ride (Driver Only)
```
POST /api/rides
Authorization: Bearer <token>
Content-Type: application/json

{
  "vehicleId": 1,
  "source": "New York",
  "destination": "Boston",
  "rideDate": "2024-02-01",
  "rideTime": "10:00:00",
  "totalSeats": 4,
  "notes": "Comfortable ride with AC"
}
```

### 2. Search Rides
```
GET /api/rides/search?source=New York&destination=Boston&rideDate=2024-02-01
```

### 3. Get Ride Details
```
GET /api/rides/{rideId}
```

### 4. Book a Seat (Passenger)
```
POST /api/rides/{rideId}/book
Authorization: Bearer <token>
Content-Type: application/json

{
  "seatsBooked": 1
}
```

### 5. Update Ride (Driver Only)
```
PUT /api/rides/{rideId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "source": "Updated Source",
  "destination": "Updated Destination",
  ...
}
```

### 6. Cancel Ride (Driver Only)
```
DELETE /api/rides/{rideId}
Authorization: Bearer <token>
```

### 7. Get My Rides (Driver)
```
GET /api/rides/my-rides
Authorization: Bearer <token>
```

### 8. Get My Bookings (Passenger)
```
GET /api/rides/my-bookings
Authorization: Bearer <token>
```

### 9. Update Ride Status (Driver Only)
```
PUT /api/rides/{rideId}/status?status=IN_PROGRESS
Authorization: Bearer <token>
```

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Eureka Server running on port 8761
- User Service running on port 8081

### Database Setup
1. Create MySQL database:
```sql
CREATE DATABASE ride_db;
```

2. Update `application.properties` with your MySQL credentials if needed.

### Build and Run
```bash
# Navigate to ride-service directory
cd ride-service

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Verify Service Registration
Once the service is running, check Eureka Dashboard at:
- http://localhost:8761
- Look for `ride-service` in the registered services

## Integration with User Service

The Ride Service uses Feign Client to communicate with User Service:
- Fetches driver profile information
- Fetches vehicle details
- Validates vehicle ownership

## Business Logic

### Ride Posting
- Only drivers can post rides
- Vehicle must belong to the driver
- Total seats must be at least 2 (driver + passenger)
- Ride date must be today or in the future

### Ride Searching
- Search by source, destination, and/or date
- Only shows rides with available seats
- Only shows active rides (POSTED, BOOKED)

### Seat Booking
- Passengers can book seats on available rides
- Driver cannot book their own ride
- Available seats must be sufficient
- Booking automatically confirms and reduces available seats

### Ride Management
- Only ride owner (driver) can update/cancel ride
- Cannot update ride with active bookings
- Cancelling ride cancels all active bookings

## Notes
- All endpoints require authentication except search and get ride details
- User ID is extracted from gateway headers (X-User-Id)
- Vehicle details are auto-filled from User Service
- Seat availability is automatically managed

---

**Document Version:** 1.0.0  
**Last Updated:** 2024-01-01  
**Service Version:** 1.0.0

