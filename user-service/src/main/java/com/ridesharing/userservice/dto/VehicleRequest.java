package com.ridesharing.userservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vehicle Request DTO
 * Data transfer object for adding/updating vehicle information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {
    
    /**
     * Vehicle model/make (e.g., "Toyota Camry")
     */
    @NotBlank(message = "Vehicle model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;
    
    /**
     * Vehicle license plate number
     */
    @NotBlank(message = "License plate is required")
    @Size(max = 20, message = "License plate must not exceed 20 characters")
    private String licensePlate;
    
    /**
     * Vehicle color
     */
    @Size(max = 50, message = "Color must not exceed 50 characters")
    private String color;
    
    /**
     * Maximum seating capacity (including driver)
     * Must be at least 2 (driver + at least 1 passenger)
     */
    @NotNull(message = "Capacity is required")
    @Min(value = 2, message = "Capacity must be at least 2")
    private Integer capacity;
    
    /**
     * Vehicle year
     */
    private Integer year;
}

