package com.ridesharing.rideservice.dto;

import lombok.Data;

/**
 * User Rating Response DTO
 * Contains average rating and total review count for a user
 */
@Data
public class UserRatingResponse {
    
    private Long userId;
    private Double averageRating;
    private Long totalReviews;
    private Long driverReviews; // Reviews as driver
    private Long passengerReviews; // Reviews as passenger
    private Double driverAverageRating;
    private Double passengerAverageRating;
}
