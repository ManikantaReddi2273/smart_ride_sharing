package com.ridesharing.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment Request DTO
 * Data transfer object for initiating a payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    /**
     * Booking ID from Ride Service
     */
    @NotNull(message = "Booking ID is required")
    private Long bookingId;
    
    /**
     * Passenger's user ID
     */
    @NotNull(message = "Passenger ID is required")
    private Long passengerId;
    
    /**
     * Driver's user ID
     */
    @NotNull(message = "Driver ID is required")
    private Long driverId;
    
    /**
     * Total amount to be paid (in paise for Razorpay)
     */
    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than 0")
    private Double amount;
    
    /**
     * Ride fare (before platform fee)
     */
    @NotNull(message = "Fare is required")
    @Min(value = 0, message = "Fare must be 0 or greater")
    private Double fare;
    
    /**
     * Currency code (default: INR)
     */
    private String currency = "INR";
}
