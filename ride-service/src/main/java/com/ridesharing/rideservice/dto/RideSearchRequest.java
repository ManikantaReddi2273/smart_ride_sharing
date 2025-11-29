package com.ridesharing.rideservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Ride Search Request DTO
 * Data transfer object for searching rides with filters
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideSearchRequest {
    
    /**
     * Source location (required - partial match)
     */
    private String source;
    
    /**
     * Source latitude (optional - enables intelligent route matching)
     */
    private Double sourceLatitude;
    
    /**
     * Source longitude (optional - enables intelligent route matching)
     */
    private Double sourceLongitude;
    
    /**
     * Destination location (required - partial match)
     */
    private String destination;
    
    /**
     * Destination latitude (optional - enables intelligent route matching)
     */
    private Double destinationLatitude;
    
    /**
     * Destination longitude (optional - enables intelligent route matching)
     */
    private Double destinationLongitude;
    
    /**
     * Ride date (required)
     */
    private LocalDate rideDate;
    
    /**
     * Minimum price filter (optional)
     */
    private Double minPrice;
    
    /**
     * Maximum price filter (optional)
     */
    private Double maxPrice;
    
    /**
     * Vehicle type filter (optional - e.g., "Sedan", "SUV", "Hatchback")
     */
    private String vehicleType;
    
    /**
     * Minimum driver rating filter (optional - 0 to 5)
     */
    private Integer minRating;
}

