package com.ridesharing.rideservice.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Ride Request DTO
 * Data transfer object for creating/updating a ride
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideRequest {
    
    /**
     * Vehicle ID (from User Service)
     * Vehicle details will be fetched from User Service
     */
    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;
    
    /**
     * Source location name
     */
    @NotBlank(message = "Source is required")
    @Size(max = 255, message = "Source must not exceed 255 characters")
    private String source;
    
    /**
     * Source latitude (optional - if provided, skips geocoding)
     */
    private Double sourceLatitude;
    
    /**
     * Source longitude (optional - if provided, skips geocoding)
     */
    private Double sourceLongitude;
    
    /**
     * Destination location name
     */
    @NotBlank(message = "Destination is required")
    @Size(max = 255, message = "Destination must not exceed 255 characters")
    private String destination;
    
    /**
     * Destination latitude (optional - if provided, skips geocoding)
     */
    private Double destinationLatitude;
    
    /**
     * Destination longitude (optional - if provided, skips geocoding)
     */
    private Double destinationLongitude;
    
    /**
     * Date of the ride
     * Must be today or in the future
     */
    @NotNull(message = "Ride date is required")
    @FutureOrPresent(message = "Ride date must be today or in the future")
    private LocalDate rideDate;
    
    /**
     * Time of the ride
     */
    @NotNull(message = "Ride time is required")
    private LocalTime rideTime;
    
    /**
     * Total number of seats available (including driver)
     * Must be at least 2 (driver + at least 1 passenger)
     */
    @NotNull(message = "Total seats is required")
    @Min(value = 2, message = "Total seats must be at least 2")
    private Integer totalSeats;
    
    /**
     * Additional notes or instructions
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}

