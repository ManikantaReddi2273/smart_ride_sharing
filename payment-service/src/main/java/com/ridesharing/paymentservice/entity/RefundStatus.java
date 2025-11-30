package com.ridesharing.paymentservice.entity;

/**
 * Refund Status Enumeration
 * Represents the current status of a refund
 */
public enum RefundStatus {
    /**
     * Refund has been initiated and is pending
     */
    PENDING,
    
    /**
     * Refund has been successfully processed
     */
    SUCCESS,
    
    /**
     * Refund has failed
     */
    FAILED
}
