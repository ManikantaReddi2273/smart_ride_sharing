package com.ridesharing.rideservice.dto;

import com.ridesharing.rideservice.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Review Response DTO
 * Used for returning review data
 */
@Data
public class ReviewResponse {
    
    private Long id;
    private Long bookingId;
    private Long reviewerId;
    private String reviewerName;
    private Long revieweeId;
    private String revieweeName;
    private Integer rating;
    private String comment;
    private Review.ReviewType reviewType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
