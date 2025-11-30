package com.ridesharing.paymentservice.controller;

import com.ridesharing.paymentservice.dto.PaymentOrderResponse;
import com.ridesharing.paymentservice.dto.PaymentRequest;
import com.ridesharing.paymentservice.dto.PaymentVerificationRequest;
import com.ridesharing.paymentservice.dto.PaymentVerificationResponse;
import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.Wallet;
import com.ridesharing.paymentservice.entity.WalletTransaction;
import com.ridesharing.paymentservice.service.PaymentService;
import com.ridesharing.paymentservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment Controller
 * Handles payment-related endpoints
 * CORS is handled by API Gateway - no need for @CrossOrigin annotation
 */
@RestController
@RequestMapping("/api/payments")
@Slf4j
public class PaymentController {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private WalletService walletService;
    
    /**
     * Initiate payment - create payment order
     * POST /api/payments/initiate
     * Requires authentication.
     * 
     * @param request Payment request
     * @return Payment order response with Razorpay order details
     */
    @PostMapping("/initiate")
    public ResponseEntity<PaymentOrderResponse> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {
        log.info("Payment initiation request: bookingId={}, amount={}", 
            request.getBookingId(), request.getAmount());
        
        PaymentOrderResponse response = paymentService.initiatePayment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Verify payment - verify Razorpay signature
     * POST /api/payments/verify
     * Requires authentication.
     * 
     * @param request Payment verification request
     * @return Payment verification response
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request) {
        log.info("Payment verification request: paymentId={}", request.getPaymentId());
        
        PaymentVerificationResponse response = paymentService.verifyPayment(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Get payment by ID
     * GET /api/payments/{paymentId}
     * Requires authentication.
     * 
     * @param paymentId Payment ID
     * @return Payment details
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        return new ResponseEntity<>(payment, HttpStatus.OK);
    }
    
    /**
     * Get payment by booking ID
     * GET /api/payments/booking/{bookingId}
     * Requires authentication.
     * 
     * @param bookingId Booking ID
     * @return Payment details
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<Payment> getPaymentByBookingId(@PathVariable Long bookingId) {
        Payment payment = paymentService.getPaymentByBookingId(bookingId);
        return new ResponseEntity<>(payment, HttpStatus.OK);
    }
    
    /**
     * Get transaction history for passenger
     * GET /api/payments/transactions/passenger/{passengerId}
     * Requires authentication.
     * 
     * @param passengerId Passenger ID
     * @return List of payments
     */
    @GetMapping("/transactions/passenger/{passengerId}")
    public ResponseEntity<List<Payment>> getPassengerTransactions(@PathVariable Long passengerId) {
        List<Payment> payments = paymentService.getPaymentsByPassengerId(passengerId);
        return new ResponseEntity<>(payments, HttpStatus.OK);
    }
    
    /**
     * Get transaction history for driver
     * GET /api/payments/transactions/driver/{driverId}
     * Requires authentication.
     * 
     * @param driverId Driver ID
     * @return List of payments
     */
    @GetMapping("/transactions/driver/{driverId}")
    public ResponseEntity<List<Payment>> getDriverTransactions(@PathVariable Long driverId) {
        List<Payment> payments = paymentService.getPaymentsByDriverId(driverId);
        return new ResponseEntity<>(payments, HttpStatus.OK);
    }
    
    /**
     * Get wallet balance
     * GET /api/payments/wallet/{userId}
     * Requires authentication.
     * Auto-creates wallet if it doesn't exist.
     * 
     * @param userId User ID
     * @return Wallet balance
     */
    @GetMapping("/wallet/{userId}")
    public ResponseEntity<Map<String, Object>> getWalletBalance(@PathVariable Long userId) {
        // Get or create wallet (this will create if doesn't exist in a write transaction)
        Wallet wallet = walletService.getOrCreateWallet(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("balance", wallet.getBalance());
        response.put("currency", wallet.getCurrency() != null ? wallet.getCurrency() : "INR");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Get wallet transactions
     * GET /api/payments/wallet/{userId}/transactions
     * Requires authentication.
     * Auto-creates wallet if it doesn't exist.
     * 
     * @param userId User ID
     * @return List of wallet transactions
     */
    @GetMapping("/wallet/{userId}/transactions")
    public ResponseEntity<List<WalletTransaction>> getWalletTransactions(@PathVariable Long userId) {
        // Get or create wallet first (this will create if doesn't exist in a write transaction)
        walletService.getOrCreateWallet(userId);
        // Then get transactions (read-only)
        List<WalletTransaction> transactions = walletService.getWalletTransactions(userId);
        return new ResponseEntity<>(transactions, HttpStatus.OK);
    }
    
    /**
     * Get payment order for retry
     * GET /api/payments/{paymentId}/order
     * Requires authentication. Returns payment order details for retry payment.
     * 
     * @param paymentId Payment ID
     * @return Payment order response
     */
    @GetMapping("/{paymentId}/order")
    public ResponseEntity<PaymentOrderResponse> getPaymentOrderForRetry(@PathVariable Long paymentId) {
        PaymentOrderResponse response = paymentService.getPaymentOrderForRetry(paymentId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Credit driver wallet after ride completion
     * POST /api/payments/wallet/credit
     * Requires authentication. Called by Ride Service after ride completion.
     * 
     * @param paymentId Payment ID
     * @return Success response
     */
    @PostMapping("/wallet/credit/{paymentId}")
    public ResponseEntity<Map<String, Object>> creditDriverWallet(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        walletService.creditDriverWalletAfterRideCompletion(payment);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Wallet credited successfully");
        response.put("paymentId", paymentId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
