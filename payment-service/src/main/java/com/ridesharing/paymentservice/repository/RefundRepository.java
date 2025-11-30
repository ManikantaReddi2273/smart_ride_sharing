package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Refund Repository
 * Data access layer for Refund entity
 */
@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    
    /**
     * Find all refunds for a payment
     * @param payment Payment entity
     * @return List of refunds for the payment
     */
    List<Refund> findByPayment(Payment payment);
    
    /**
     * Find refund by Razorpay refund ID
     * @param razorpayRefundId Razorpay refund ID
     * @return Optional refund if exists
     */
    Optional<Refund> findByRazorpayRefundId(String razorpayRefundId);
}
