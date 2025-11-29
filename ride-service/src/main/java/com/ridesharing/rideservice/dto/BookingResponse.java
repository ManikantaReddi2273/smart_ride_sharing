package com.ridesharing.rideservice.dto;

import com.ridesharing.rideservice.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Booking Response DTO
 * Contains booking information for responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    
    private Long id;
    private Long rideId;
    private Long passengerId;
    private String passengerName;
    private String passengerEmail;
    private Integer seatsBooked;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Passenger-specific fare details
    private String passengerSource;
    private String passengerDestination;
    private Double passengerDistanceKm;
    private Double passengerFare;
    private String currency;
    
    // Ride details (optional - can be included in response)
    private RideResponse rideDetails;
}

