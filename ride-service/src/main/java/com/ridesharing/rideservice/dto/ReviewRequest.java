package com.ridesharing.rideservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Review Request DTO
 * Used for creating a new review
 */
@Data
public class ReviewRequest {
    
    /**
     * Booking ID for which the review is being written
     */
    @NotNull(message = "Booking ID is required")
    private Long bookingId;
    
    /**
     * Rating from 1 to 5 stars
     */
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;
    
    /**
     * Optional comment/review text
     */
    private String comment;
}
