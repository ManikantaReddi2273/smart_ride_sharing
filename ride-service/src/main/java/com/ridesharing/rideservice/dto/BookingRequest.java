package com.ridesharing.rideservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Booking Request DTO
 * Data transfer object for booking a seat
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {
    
    /**
     * Number of seats to book
     * Default is 1
     */
    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "Must book at least 1 seat")
    private Integer seatsBooked = 1;

    /**
     * Optional passenger source location.
     * If null or blank, the ride's source will be used.
     */
    private String passengerSource;

    /**
     * Optional passenger destination location.
     * If null or blank, the ride's destination will be used.
     */
    private String passengerDestination;
}

