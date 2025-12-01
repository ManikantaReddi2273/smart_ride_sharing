package com.ridesharing.rideservice.controller;

import com.ridesharing.rideservice.dto.ReviewRequest;
import com.ridesharing.rideservice.dto.ReviewResponse;
import com.ridesharing.rideservice.dto.UserRatingResponse;
import com.ridesharing.rideservice.service.ReviewService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Review Controller
 * Handles review and rating endpoints
 */
@RestController
@RequestMapping("/api/reviews")
@Slf4j
public class ReviewController {
    
    @Autowired
    private ReviewService reviewService;
    
    /**
     * Create a review from passenger to driver
     * POST /api/reviews/passenger/{bookingId}
     * 
     * @param bookingId Booking ID
     * @param passengerId Passenger's user ID from gateway header
     * @param request Review request
     * @return ReviewResponse
     */
    @PostMapping("/passenger/{bookingId}")
    public ResponseEntity<ReviewResponse> createPassengerReview(
            @PathVariable Long bookingId,
            @RequestHeader("X-User-Id") Long passengerId,
            @Valid @RequestBody ReviewRequest request) {
        try {
            log.info("Creating passenger review: bookingId={}, passengerId={}, rating={}", 
                    bookingId, passengerId, request.getRating());
            ReviewResponse response = reviewService.createPassengerReview(bookingId, passengerId, request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error in createPassengerReview endpoint: {}", e.getMessage(), e);
            throw e; // Re-throw to let Spring handle it
        }
    }
    
    /**
     * Create a review from driver to passenger
     * POST /api/reviews/driver/{bookingId}
     * 
     * @param bookingId Booking ID
     * @param driverId Driver's user ID from gateway header
     * @param request Review request
     * @return ReviewResponse
     */
    @PostMapping("/driver/{bookingId}")
    public ResponseEntity<ReviewResponse> createDriverReview(
            @PathVariable Long bookingId,
            @RequestHeader("X-User-Id") Long driverId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse response = reviewService.createDriverReview(bookingId, driverId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all reviews for a user
     * GET /api/reviews/user/{userId}
     * 
     * @param userId User ID
     * @return List of ReviewResponse
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponse>> getUserReviews(@PathVariable Long userId) {
        List<ReviewResponse> reviews = reviewService.getUserReviews(userId);
        return new ResponseEntity<>(reviews, HttpStatus.OK);
    }
    
    /**
     * Get user rating statistics
     * GET /api/reviews/user/{userId}/rating
     * 
     * @param userId User ID
     * @return UserRatingResponse
     */
    @GetMapping("/user/{userId}/rating")
    public ResponseEntity<UserRatingResponse> getUserRating(@PathVariable Long userId) {
        UserRatingResponse rating = reviewService.getUserRating(userId);
        return new ResponseEntity<>(rating, HttpStatus.OK);
    }
    
    /**
     * Get reviews for a specific booking
     * GET /api/reviews/booking/{bookingId}
     * 
     * @param bookingId Booking ID
     * @return List of ReviewResponse
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<ReviewResponse>> getBookingReviews(@PathVariable Long bookingId) {
        List<ReviewResponse> reviews = reviewService.getBookingReviews(bookingId);
        return new ResponseEntity<>(reviews, HttpStatus.OK);
    }
    
    /**
     * Check if user has reviewed a booking
     * GET /api/reviews/booking/{bookingId}/check
     * 
     * @param bookingId Booking ID
     * @param userId User ID from gateway header
     * @return Map with hasReviewed boolean
     */
    @GetMapping("/booking/{bookingId}/check")
    public ResponseEntity<Map<String, Object>> checkUserReviewed(
            @PathVariable Long bookingId,
            @RequestHeader("X-User-Id") Long userId) {
        try {
            log.info("Checking if user {} has reviewed booking {}", userId, bookingId);
            boolean hasReviewed = reviewService.hasUserReviewed(bookingId, userId);
            log.info("Review check result for user {} and booking {}: {}", userId, bookingId, hasReviewed);
            return new ResponseEntity<>(Map.of("hasReviewed", hasReviewed), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in checkUserReviewed endpoint: bookingId={}, userId={}, error={}", 
                    bookingId, userId, e.getMessage(), e);
            // Return false on error to allow review
            return new ResponseEntity<>(Map.of("hasReviewed", false), HttpStatus.OK);
        }
    }
}
