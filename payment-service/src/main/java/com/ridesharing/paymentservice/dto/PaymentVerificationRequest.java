package com.ridesharing.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment Verification Request DTO
 * Data transfer object for verifying payment with Razorpay
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationRequest {
    
    /**
     * Payment ID (internal)
     */
    @NotNull(message = "Payment ID is required")
    private Long paymentId;
    
    /**
     * Razorpay Payment ID
     */
    @NotBlank(message = "Razorpay Payment ID is required")
    private String razorpayPaymentId;
    
    /**
     * Razorpay Order ID
     */
    @NotBlank(message = "Razorpay Order ID is required")
    private String razorpayOrderId;
    
    /**
     * Razorpay Signature (for verification)
     */
    @NotBlank(message = "Razorpay Signature is required")
    private String razorpaySignature;
}
