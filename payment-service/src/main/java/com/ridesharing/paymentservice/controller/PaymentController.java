package com.ridesharing.paymentservice.controller;

import com.ridesharing.paymentservice.dto.*;
import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.Wallet;
import com.ridesharing.paymentservice.entity.WalletTransaction;
import com.ridesharing.paymentservice.service.BankAccountService;
import com.ridesharing.paymentservice.service.PaymentService;
import com.ridesharing.paymentservice.service.WalletService;
import com.ridesharing.paymentservice.service.WithdrawalService;
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
    
    @Autowired
    private BankAccountService bankAccountService;
    
    @Autowired
    private WithdrawalService withdrawalService;
    
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
        // CRITICAL: Log the exact values received from Ride Service
        log.info("📥 Payment Controller - Received payment request: bookingId={}, passengerId={}, driverId={}, amount={}, fare={}, currency={}", 
            request.getBookingId(), request.getPassengerId(), request.getDriverId(), 
            request.getAmount(), request.getFare(), request.getCurrency());
        
        // CRITICAL: Validate that fare is reasonable (not suspiciously low for multiple seats)
        // This is a safety check - if fare is less than 100, it might be per-seat instead of total
        if (request.getFare() != null && request.getFare() < 100) {
            log.warn("⚠️ WARNING: Received fare {} {} seems very low - might be per-seat fare instead of total!", 
                request.getFare(), request.getCurrency());
        }
        
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
    
    // ==================== Bank Account Endpoints ====================
    
    /**
     * Add bank account
     * POST /api/payments/bank-accounts
     * Requires authentication.
     * 
     * @param userId User ID (from JWT token)
     * @param request Bank account request
     * @return Bank account response
     */
    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountResponse> addBankAccount(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody BankAccountRequest request) {
        BankAccountResponse response = bankAccountService.addBankAccount(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all bank accounts for user
     * GET /api/payments/bank-accounts
     * Requires authentication.
     * 
     * @param userId User ID (from JWT token)
     * @return List of bank accounts
     */
    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts(
            @RequestHeader("X-User-Id") Long userId) {
        List<BankAccountResponse> accounts = bankAccountService.getBankAccounts(userId);
        return new ResponseEntity<>(accounts, HttpStatus.OK);
    }
    
    /**
     * Get bank account by ID
     * GET /api/payments/bank-accounts/{accountId}
     * Requires authentication.
     * 
     * @param accountId Bank account ID
     * @param userId User ID (from JWT token)
     * @return Bank account response
     */
    @GetMapping("/bank-accounts/{accountId}")
    public ResponseEntity<BankAccountResponse> getBankAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Id") Long userId) {
        BankAccountResponse response = bankAccountService.getBankAccount(accountId, userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Delete bank account
     * DELETE /api/payments/bank-accounts/{accountId}
     * Requires authentication.
     * 
     * @param accountId Bank account ID
     * @param userId User ID (from JWT token)
     * @return Success response
     */
    @DeleteMapping("/bank-accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> deleteBankAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Id") Long userId) {
        bankAccountService.deleteBankAccount(accountId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bank account deleted successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Set default bank account
     * PUT /api/payments/bank-accounts/{accountId}/default
     * Requires authentication.
     * 
     * @param accountId Bank account ID
     * @param userId User ID (from JWT token)
     * @return Bank account response
     */
    @PutMapping("/bank-accounts/{accountId}/default")
    public ResponseEntity<BankAccountResponse> setDefaultBankAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Id") Long userId) {
        BankAccountResponse response = bankAccountService.setDefaultBankAccount(accountId, userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    // ==================== Withdrawal Endpoints ====================
    
    /**
     * Request withdrawal
     * POST /api/payments/withdrawals
     * Requires authentication.
     * 
     * @param userId User ID (from JWT token)
     * @param request Withdrawal request
     * @return Withdrawal response
     */
    @PostMapping("/withdrawals")
    public ResponseEntity<WithdrawalResponse> requestWithdrawal(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody WithdrawalRequest request) {
        WithdrawalResponse response = withdrawalService.requestWithdrawal(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all withdrawals for user
     * GET /api/payments/withdrawals
     * Requires authentication.
     * 
     * @param userId User ID (from JWT token)
     * @return List of withdrawals
     */
    @GetMapping("/withdrawals")
    public ResponseEntity<List<WithdrawalResponse>> getWithdrawals(
            @RequestHeader("X-User-Id") Long userId) {
        List<WithdrawalResponse> withdrawals = withdrawalService.getWithdrawals(userId);
        return new ResponseEntity<>(withdrawals, HttpStatus.OK);
    }
    
    /**
     * Get withdrawal by ID
     * GET /api/payments/withdrawals/{withdrawalId}
     * Requires authentication.
     * 
     * @param withdrawalId Withdrawal ID
     * @param userId User ID (from JWT token)
     * @return Withdrawal response
     */
    @GetMapping("/withdrawals/{withdrawalId}")
    public ResponseEntity<WithdrawalResponse> getWithdrawal(
            @PathVariable Long withdrawalId,
            @RequestHeader("X-User-Id") Long userId) {
        WithdrawalResponse response = withdrawalService.getWithdrawal(withdrawalId, userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
