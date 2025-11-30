package com.ridesharing.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payment Entity
 * Represents a payment transaction for a booking
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_booking_id", columnList = "booking_id"),
    @Index(name = "idx_passenger_id", columnList = "passenger_id"),
    @Index(name = "idx_driver_id", columnList = "driver_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Booking ID from Ride Service (reference, not FK)
     */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;
    
    /**
     * Passenger's user ID (from User Service)
     */
    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;
    
    /**
     * Driver's user ID (from User Service)
     */
    @Column(name = "driver_id", nullable = false)
    private Long driverId;
    
    /**
     * Total amount paid by passenger
     */
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    /**
     * Ride fare (before platform fee)
     */
    @Column(name = "fare", nullable = false)
    private Double fare;
    
    /**
     * Platform commission/fee
     */
    @Column(name = "platform_fee")
    private Double platformFee;
    
    /**
     * Current status of the payment
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    /**
     * Payment method/gateway used
     */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod = "RAZORPAY";
    
    /**
     * Razorpay Order ID
     */
    @Column(name = "razorpay_order_id", length = 255)
    private String razorpayOrderId;
    
    /**
     * Razorpay Payment ID (after payment success)
     */
    @Column(name = "razorpay_payment_id", length = 255)
    private String razorpayPaymentId;
    
    /**
     * Razorpay Signature (for verification)
     */
    @Column(name = "razorpay_signature", length = 500)
    private String razorpaySignature;
    
    /**
     * Currency code (e.g., INR, USD)
     */
    @Column(name = "currency", length = 10)
    private String currency = "INR";
    
    /**
     * Timestamp when payment was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when payment was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Pre-persist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Pre-update callback to set update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
