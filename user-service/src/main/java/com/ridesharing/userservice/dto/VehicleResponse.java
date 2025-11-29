package com.ridesharing.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vehicle Response DTO
 * Contains vehicle information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    
    /**
     * Vehicle ID
     */
    private Long id;
    
    /**
     * Vehicle model/make
     */
    private String model;
    
    /**
     * Vehicle license plate number
     */
    private String licensePlate;
    
    /**
     * Vehicle color
     */
    private String color;
    
    /**
     * Maximum seating capacity
     */
    private Integer capacity;
    
    /**
     * Vehicle year
     */
    private Integer year;
    
    /**
     * Vehicle verification status
     */
    private Boolean isVerified;
    
    /**
     * Vehicle registration timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Vehicle last update timestamp
     */
    private LocalDateTime updatedAt;
}

