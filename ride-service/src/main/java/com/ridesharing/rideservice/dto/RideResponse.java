package com.ridesharing.rideservice.dto;

import com.ridesharing.rideservice.entity.RideStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ride Response DTO
 * Contains ride information for responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {
    
    private Long id;
    private Long driverId;
    private String driverName;
    private String driverEmail;
    private Long vehicleId;
    private String vehicleModel;
    private String vehicleLicensePlate;
    private String vehicleColor;
    private Integer vehicleCapacity;
    private String source;
    private String destination;
    private LocalDate rideDate;
    private LocalTime rideTime;
    private Integer totalSeats;
    private Integer availableSeats;
    private RideStatus status;
    // Fare-related fields (full ride)
    private Double distanceKm;
    private Double totalFare;
    private Double baseFare;
    private Double ratePerKm;
    private String currency;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

