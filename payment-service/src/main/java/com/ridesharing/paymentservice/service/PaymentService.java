package com.ridesharing.paymentservice.service;

import com.razorpay.Order;
import com.ridesharing.paymentservice.dto.PaymentOrderResponse;
import com.ridesharing.paymentservice.dto.PaymentRequest;
import com.ridesharing.paymentservice.dto.PaymentVerificationRequest;
import com.ridesharing.paymentservice.dto.PaymentVerificationResponse;
import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.PaymentStatus;
import com.ridesharing.paymentservice.exception.BadRequestException;
import com.ridesharing.paymentservice.exception.ResourceNotFoundException;
import com.ridesharing.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payment Service
 * Handles payment business logic
 */
@Service
@Slf4j
@Transactional
public class PaymentService {
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private RazorpayService razorpayService;
    
    @Autowired
    private WalletService walletService;
    
    @Value("${payment.platform.fee.percentage:10.0}")
    private Double platformFeePercentage;
    
    @Value("${razorpay.key.id}")
    private String razorpayKeyId;
    
    /**
     * Initiate payment - create payment record and Razorpay order
     * @param request Payment request
     * @return Payment order response with Razorpay order details
     */
    public PaymentOrderResponse initiatePayment(PaymentRequest request) {
        log.info("Initiating payment for bookingId={}, amount={} {}, fare={} {}", 
            request.getBookingId(), request.getAmount(), request.getCurrency(), 
            request.getFare(), request.getCurrency());
        
        // CRITICAL: Validate that fare and amount are consistent
        // They should be the same (both represent total fare for all seats)
        if (request.getFare() != null && request.getAmount() != null) {
            double fareDiff = Math.abs(request.getFare() - request.getAmount());
            if (fareDiff > 0.01) { // Allow small rounding differences
                log.warn("⚠️ WARNING: Fare ({}) and Amount ({}) differ by {} - they should be the same!", 
                    request.getFare(), request.getAmount(), fareDiff);
            }
        }
        
        // Check if payment already exists for this booking
        Optional<Payment> existingPayment = paymentRepository.findByBookingId(request.getBookingId());
        if (existingPayment.isPresent() && 
            (existingPayment.get().getStatus() == PaymentStatus.SUCCESS || 
             existingPayment.get().getStatus() == PaymentStatus.PENDING)) {
            throw new BadRequestException("Payment already exists for this booking");
        }
        
        // CRITICAL: Calculate platform fee on the total fare (which already includes all seats)
        // request.getFare() is the total fare for all seats booked (fare per seat * number of seats)
        Double fareForAllSeats = request.getFare();
        
        // CRITICAL: Validate fare is not null and is positive
        if (fareForAllSeats == null || fareForAllSeats <= 0) {
            log.error("❌ CRITICAL: Invalid fare received: {}", fareForAllSeats);
            throw new BadRequestException("Invalid fare: " + fareForAllSeats + ". Fare must be greater than 0.");
        }
        
        // CRITICAL: Log the fare received to verify it's the total for all seats
        log.info("💰 Payment Service - Received fare: {} {} (should be total for all seats, not per-seat)", 
            fareForAllSeats, request.getCurrency());
        
        Double platformFee = (fareForAllSeats * platformFeePercentage) / 100.0;
        // Round platform fee to 2 decimal places
        platformFee = Math.round(platformFee * 100.0) / 100.0;
        Double totalAmount = fareForAllSeats + platformFee;
        // Round total amount to 2 decimal places
        totalAmount = Math.round(totalAmount * 100.0) / 100.0;
        
        log.info("💰 Payment calculation - Fare (all seats): {} {}, Platform fee ({}%): {} {}, Total amount: {} {}", 
            fareForAllSeats, request.getCurrency(), platformFeePercentage, platformFee, request.getCurrency(), 
            totalAmount, request.getCurrency());
        
        // Convert amount to paise (Razorpay uses smallest currency unit)
        Long amountInPaise = Math.round(totalAmount * 100);
        
        // CRITICAL: Create receipt as a proper String (not char array)
        // Ensure bookingId is converted to String properly
        Long bookingId = request.getBookingId();
        String receipt = "booking_" + (bookingId != null ? bookingId.toString() : String.valueOf(System.currentTimeMillis()));
        // CRITICAL: Create new String instance to ensure it's not a char array
        receipt = new String(receipt);
        log.info("Creating Razorpay order for booking: bookingId={}, amount={} paise, receipt={}", 
            bookingId, amountInPaise, receipt);
        
        Order razorpayOrder;
        try {
            razorpayOrder = razorpayService.createOrder(amountInPaise, request.getCurrency(), receipt);
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for bookingId={}: {}", request.getBookingId(), e.getMessage(), e);
            throw new BadRequestException("Failed to create payment order: " + e.getMessage());
        }
        
        // Create payment record
        Payment payment = new Payment();
        payment.setBookingId(request.getBookingId());
        payment.setPassengerId(request.getPassengerId());
        payment.setDriverId(request.getDriverId());
        payment.setAmount(totalAmount);
        payment.setFare(request.getFare());
        payment.setPlatformFee(platformFee);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod("RAZORPAY");
        
        // CRITICAL: Safely extract order ID from Razorpay Order object
        // The get("id") might return char[] instead of String, causing ClassCastException
        Object orderIdObj = razorpayOrder.get("id");
        String razorpayOrderId;
        if (orderIdObj != null) {
            // Handle both String and char[] cases
            if (orderIdObj instanceof String) {
                razorpayOrderId = (String) orderIdObj;
            } else if (orderIdObj instanceof char[]) {
                razorpayOrderId = new String((char[]) orderIdObj);
            } else {
                // Fallback: convert to string safely
                razorpayOrderId = String.valueOf(orderIdObj);
            }
        } else {
            throw new RuntimeException("Razorpay order ID is null");
        }
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        
        payment = paymentRepository.save(payment);
        
        log.info("Payment initiated successfully: paymentId={}, orderId={}", 
            payment.getId(), payment.getRazorpayOrderId());
        
        // Build response
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getRazorpayOrderId());
        response.setAmount(amountInPaise);
        response.setCurrency(payment.getCurrency());
        response.setKeyId(razorpayKeyId);
        response.setBookingId(payment.getBookingId());
        
        return response;
    }
    
    /**
     * Verify payment - verify Razorpay signature and update payment status
     * @param request Payment verification request
     * @return Payment verification response
     */
    public PaymentVerificationResponse verifyPayment(PaymentVerificationRequest request) {
        log.info("Verifying payment: paymentId={}, orderId={}, paymentId={}", 
            request.getPaymentId(), request.getRazorpayOrderId(), request.getRazorpayPaymentId());
        
        // Get payment
        Payment payment = paymentRepository.findById(request.getPaymentId())
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", request.getPaymentId()));
        
        // Check if payment is already verified
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.warn("Payment already verified: paymentId={}", payment.getId());
            PaymentVerificationResponse response = new PaymentVerificationResponse();
            response.setPaymentId(payment.getId());
            response.setBookingId(payment.getBookingId());
            response.setStatus(payment.getStatus());
            response.setVerified(true);
            response.setMessage("Payment already verified");
            return response;
        }
        
        // Verify signature
        boolean isValid = razorpayService.verifySignature(
            request.getRazorpayOrderId(),
            request.getRazorpayPaymentId(),
            request.getRazorpaySignature()
        );
        
        if (!isValid) {
            log.error("Payment signature verification failed: paymentId={}", payment.getId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            
            PaymentVerificationResponse response = new PaymentVerificationResponse();
            response.setPaymentId(payment.getId());
            response.setBookingId(payment.getBookingId());
            response.setStatus(PaymentStatus.FAILED);
            response.setVerified(false);
            response.setMessage("Payment signature verification failed");
            return response;
        }
        
        // Update payment status
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment = paymentRepository.save(payment);
        
        log.info("Payment verified successfully: paymentId={}, bookingId={}", 
            payment.getId(), payment.getBookingId());
        
        // Credit driver's wallet (will be done after ride completion, but we prepare here)
        // Note: Actual wallet credit happens when ride is completed
        
        PaymentVerificationResponse response = new PaymentVerificationResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBookingId());
        response.setStatus(PaymentStatus.SUCCESS);
        response.setVerified(true);
        response.setMessage("Payment verified successfully");
        
        return response;
    }
    
    /**
     * Get payment by ID
     * @param paymentId Payment ID
     * @return Payment entity
     */
    @Transactional(readOnly = true)
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }
    
    /**
     * Get payment by booking ID
     * @param bookingId Booking ID
     * @return Payment entity
     */
    @Transactional(readOnly = true)
    public Payment getPaymentByBookingId(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "bookingId", bookingId));
    }
    
    /**
     * Get all payments by passenger ID
     * @param passengerId Passenger ID
     * @return List of payments
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByPassengerId(Long passengerId) {
        return paymentRepository.findByPassengerId(passengerId);
    }
    
    /**
     * Get all payments by driver ID
     * @param driverId Driver ID
     * @return List of payments
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByDriverId(Long driverId) {
        return paymentRepository.findByDriverId(driverId);
    }
    
    /**
     * Get payment order details for retry
     * Returns payment order response with Razorpay details for existing payment
     * @param paymentId Payment ID
     * @return Payment order response
     */
    @Transactional(readOnly = true)
    public PaymentOrderResponse getPaymentOrderForRetry(Long paymentId) {
        Payment payment = getPaymentById(paymentId);
        
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Payment already completed. Cannot retry.");
        }
        
        if (payment.getRazorpayOrderId() == null || payment.getRazorpayOrderId().isEmpty()) {
            throw new BadRequestException("Payment order not found. Please create a new payment.");
        }
        
        // Convert amount to paise
        Long amountInPaise = Math.round(payment.getAmount() * 100);
        
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getRazorpayOrderId());
        response.setAmount(amountInPaise);
        response.setCurrency(payment.getCurrency() != null ? payment.getCurrency() : "INR");
        response.setKeyId(razorpayKeyId);
        response.setBookingId(payment.getBookingId());
        
        return response;
    }
}
