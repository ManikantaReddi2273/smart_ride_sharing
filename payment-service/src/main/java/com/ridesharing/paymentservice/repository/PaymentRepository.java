package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Payment Repository
 * Data access layer for Payment entity
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    /**
     * Find payment by booking ID
     * @param bookingId Booking ID
     * @return Optional payment if exists
     */
    Optional<Payment> findByBookingId(Long bookingId);
    
    /**
     * Find all payments by passenger ID
     * @param passengerId Passenger's user ID
     * @return List of payments made by the passenger
     */
    List<Payment> findByPassengerId(Long passengerId);
    
    /**
     * Find all payments by driver ID
     * @param driverId Driver's user ID
     * @return List of payments received by the driver
     */
    List<Payment> findByDriverId(Long driverId);
    
    /**
     * Find payment by Razorpay order ID
     * @param razorpayOrderId Razorpay order ID
     * @return Optional payment if exists
     */
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    
    /**
     * Find payment by Razorpay payment ID
     * @param razorpayPaymentId Razorpay payment ID
     * @return Optional payment if exists
     */
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    
    /**
     * Find all payments by status
     * @param status Payment status
     * @return List of payments with the specified status
     */
    List<Payment> findByStatus(PaymentStatus status);
}
