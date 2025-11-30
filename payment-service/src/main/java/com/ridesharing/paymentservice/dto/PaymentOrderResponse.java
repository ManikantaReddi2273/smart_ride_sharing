package com.ridesharing.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment Order Response DTO
 * Response containing Razorpay order details for frontend
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    
    /**
     * Payment ID (internal)
     */
    private Long paymentId;
    
    /**
     * Razorpay Order ID
     */
    private String orderId;
    
    /**
     * Amount in paise (Razorpay format)
     */
    private Long amount;
    
    /**
     * Currency code
     */
    private String currency;
    
    /**
     * Razorpay Key ID (for frontend)
     */
    private String keyId;
    
    /**
     * Booking ID
     */
    private Long bookingId;
}
