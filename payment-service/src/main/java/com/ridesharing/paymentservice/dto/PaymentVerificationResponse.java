package com.ridesharing.paymentservice.dto;

import com.ridesharing.paymentservice.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment Verification Response DTO
 * Response after payment verification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationResponse {
    
    /**
     * Payment ID
     */
    private Long paymentId;
    
    /**
     * Booking ID
     */
    private Long bookingId;
    
    /**
     * Payment status
     */
    private PaymentStatus status;
    
    /**
     * Verification success flag
     */
    private Boolean verified;
    
    /**
     * Message
     */
    private String message;
}
