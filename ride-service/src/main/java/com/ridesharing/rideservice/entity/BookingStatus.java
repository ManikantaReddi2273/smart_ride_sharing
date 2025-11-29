package com.ridesharing.rideservice.entity;

/**
 * Booking Status Enumeration
 * Represents the current status of a booking
 */
public enum BookingStatus {
    /**
     * Booking has been created and is pending confirmation
     */
    PENDING,
    
    /**
     * Booking has been confirmed
     */
    CONFIRMED,
    
    /**
     * Booking has been cancelled
     */
    CANCELLED,
    
    /**
     * Ride has been completed
     */
    COMPLETED
}

