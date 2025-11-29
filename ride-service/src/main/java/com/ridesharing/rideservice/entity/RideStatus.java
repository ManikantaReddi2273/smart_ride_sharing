package com.ridesharing.rideservice.entity;

/**
 * Ride Status Enumeration
 * Represents the current status of a ride
 */
public enum RideStatus {
    /**
     * Ride has been posted by driver and is available for booking
     */
    POSTED,
    
    /**
     * Ride has been booked by at least one passenger
     */
    BOOKED,
    
    /**
     * Ride is currently in progress
     */
    IN_PROGRESS,
    
    /**
     * Ride has been completed
     */
    COMPLETED,
    
    /**
     * Ride has been cancelled by driver or passenger
     */
    CANCELLED
}

