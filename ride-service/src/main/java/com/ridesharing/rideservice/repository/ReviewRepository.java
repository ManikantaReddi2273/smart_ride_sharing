package com.ridesharing.rideservice.repository;

import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Review Repository
 * Data access layer for Review entity
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    /**
     * Find review by booking and reviewer
     * @param booking Booking entity
     * @param reviewerId Reviewer's user ID
     * @return Optional review if exists
     */
    Optional<Review> findByBookingAndReviewerId(Booking booking, Long reviewerId);
    
    /**
     * Find all reviews for a specific user (as reviewee)
     * @param revieweeId User ID of the person being reviewed
     * @return List of reviews
     */
    List<Review> findByRevieweeId(Long revieweeId);
    
    /**
     * Find all reviews written by a specific user (as reviewer)
     * @param reviewerId User ID of the person writing the review
     * @return List of reviews
     */
    List<Review> findByReviewerId(Long reviewerId);
    
    /**
     * Find all reviews for a booking
     * @param booking Booking entity
     * @return List of reviews
     */
    List<Review> findByBooking(Booking booking);
    
    /**
     * Calculate average rating for a user
     * @param revieweeId User ID of the person being reviewed
     * @return Average rating (Double)
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.revieweeId = :revieweeId")
    Double calculateAverageRating(@Param("revieweeId") Long revieweeId);
    
    /**
     * Count total reviews for a user
     * @param revieweeId User ID of the person being reviewed
     * @return Count of reviews
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.revieweeId = :revieweeId")
    Long countReviewsByRevieweeId(@Param("revieweeId") Long revieweeId);
    
    /**
     * Find reviews by review type
     * @param revieweeId User ID
     * @param reviewType Review type
     * @return List of reviews
     */
    List<Review> findByRevieweeIdAndReviewType(Long revieweeId, Review.ReviewType reviewType);
}
