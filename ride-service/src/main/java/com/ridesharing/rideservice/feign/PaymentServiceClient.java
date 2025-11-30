package com.ridesharing.rideservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign Client for Payment Service
 * Communicates with Payment Service for payment processing
 */
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {
    
    /**
     * Initiate payment - create payment order
     * @param request Payment request containing booking details and amount
     * @return Payment order response with Razorpay order details
     */
    @PostMapping("/api/payments/initiate")
    Map<String, Object> initiatePayment(@RequestBody Map<String, Object> request);
    
    /**
     * Verify payment - verify Razorpay signature
     * @param request Payment verification request
     * @return Payment verification response
     */
    @PostMapping("/api/payments/verify")
    Map<String, Object> verifyPayment(@RequestBody Map<String, Object> request);
    
    /**
     * Credit driver wallet after ride completion
     * @param paymentId Payment ID
     * @return Success response
     */
    @PostMapping("/api/payments/wallet/credit/{paymentId}")
    Map<String, Object> creditDriverWallet(@org.springframework.web.bind.annotation.PathVariable("paymentId") Long paymentId);
}
