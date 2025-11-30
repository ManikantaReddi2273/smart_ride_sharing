package com.ridesharing.paymentservice.entity;

/**
 * Payment Status Enumeration
 * Represents the current status of a payment
 */
public enum PaymentStatus {
    /**
     * Payment order has been created and is pending user payment
     */
    PENDING,
    
    /**
     * Payment has been successfully completed
     */
    SUCCESS,
    
    /**
     * Payment has failed
     */
    FAILED,
    
    /**
     * Payment has been refunded
     */
    REFUNDED,
    
    /**
     * Payment has been cancelled
     */
    CANCELLED
}
