package com.ridesharing.rideservice.dto;

import com.ridesharing.rideservice.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

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
    
    // Payment details (for payment integration)
    private Long paymentId;
    private Map<String, Object> paymentOrder; // Razorpay order details (orderId, keyId, amount, etc.)
    
    // OTP verification fields
    private Boolean driverConfirmed;
    private Boolean passengerConfirmed;
    private String otp; // Only include if needed for debugging (usually not sent to frontend)
    private Boolean hasOtp; // Indicates if OTP has been generated (without exposing the actual OTP)
    
    // Ride details (optional - can be included in response)
    private RideResponse rideDetails;
}

