package com.ridesharing.rideservice.service;

import com.ridesharing.rideservice.dto.ReviewRequest;
import com.ridesharing.rideservice.dto.ReviewResponse;
import com.ridesharing.rideservice.dto.UserRatingResponse;
import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.BookingStatus;
import com.ridesharing.rideservice.entity.Review;
import com.ridesharing.rideservice.exception.BadRequestException;
import com.ridesharing.rideservice.exception.ResourceNotFoundException;
import com.ridesharing.rideservice.feign.UserServiceClient;
import com.ridesharing.rideservice.repository.BookingRepository;
import com.ridesharing.rideservice.repository.ReviewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Review Service
 * Handles review creation and rating calculations
 */
@Service
@Slf4j
@Transactional
public class ReviewService {
    
    @Autowired
    private ReviewRepository reviewRepository;
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Autowired
    private UserServiceClient userServiceClient;
    
    /**
     * Create a review from passenger to driver
     * @param bookingId Booking ID
     * @param passengerId Passenger's user ID (reviewer)
     * @param request Review request
     * @return ReviewResponse
     */
    public ReviewResponse createPassengerReview(Long bookingId, Long passengerId, ReviewRequest request) {
        try {
            // Validate booking ID matches
            if (!request.getBookingId().equals(bookingId)) {
                throw new BadRequestException("Booking ID in request does not match path parameter");
            }
            
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
            
            // Validate passenger owns the booking
            if (!booking.getPassengerId().equals(passengerId)) {
                throw new BadRequestException("Only the passenger who made the booking can review");
            }
            
            // Validate booking is completed
            if (booking.getStatus() != BookingStatus.COMPLETED) {
                throw new BadRequestException("Can only review completed bookings");
            }
            
            // Check if review already exists
            if (reviewRepository.findByBookingAndReviewerId(booking, passengerId).isPresent()) {
                throw new BadRequestException("You have already reviewed this ride");
            }
            
            // Get driver ID from ride
            Long driverId = booking.getRide().getDriverId();
            
            // Create review
            Review review = new Review();
            review.setBooking(booking);
            review.setReviewerId(passengerId);
            review.setRevieweeId(driverId);
            review.setRating(request.getRating());
            review.setComment(request.getComment());
            review.setReviewType(Review.ReviewType.PASSENGER_TO_DRIVER);
            
            review = reviewRepository.save(review);
            log.info("Created passenger review: reviewId={}, bookingId={}, passengerId={}, driverId={}, rating={}",
                    review.getId(), bookingId, passengerId, driverId, request.getRating());
            
            return buildReviewResponse(review);
        } catch (BadRequestException | ResourceNotFoundException e) {
            // Re-throw business exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error creating passenger review: bookingId={}, passengerId={}, error={}", 
                    bookingId, passengerId, e.getMessage(), e);
            throw new BadRequestException("Failed to create review: " + e.getMessage());
        }
    }
    
    /**
     * Create a review from driver to passenger
     * @param bookingId Booking ID
     * @param driverId Driver's user ID (reviewer)
     * @param request Review request
     * @return ReviewResponse
     */
    public ReviewResponse createDriverReview(Long bookingId, Long driverId, ReviewRequest request) {
        // Validate booking ID matches
        if (!request.getBookingId().equals(bookingId)) {
            throw new BadRequestException("Booking ID in request does not match path parameter");
        }
        
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        
        // Validate driver owns the ride
        if (!booking.getRide().getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can review passengers");
        }
        
        // Validate booking is completed
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BadRequestException("Can only review completed bookings");
        }
        
        // Check if review already exists
        if (reviewRepository.findByBookingAndReviewerId(booking, driverId).isPresent()) {
            throw new BadRequestException("You have already reviewed this passenger");
        }
        
        // Get passenger ID from booking
        Long passengerId = booking.getPassengerId();
        
        // Create review
        Review review = new Review();
        review.setBooking(booking);
        review.setReviewerId(driverId);
        review.setRevieweeId(passengerId);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setReviewType(Review.ReviewType.DRIVER_TO_PASSENGER);
        
        review = reviewRepository.save(review);
        log.info("Created driver review: reviewId={}, bookingId={}, driverId={}, passengerId={}, rating={}",
                review.getId(), bookingId, driverId, passengerId, request.getRating());
        
        return buildReviewResponse(review);
    }
    
    /**
     * Get all reviews for a user (as reviewee)
     * @param userId User ID
     * @return List of ReviewResponse
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getUserReviews(Long userId) {
        List<Review> reviews = reviewRepository.findByRevieweeId(userId);
        return reviews.stream()
                .map(this::buildReviewResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get user rating statistics
     * @param userId User ID
     * @return UserRatingResponse with average rating and counts
     */
    @Transactional(readOnly = true)
    public UserRatingResponse getUserRating(Long userId) {
        try {
            log.debug("Fetching user rating for userId={}", userId);
            
            UserRatingResponse response = new UserRatingResponse();
            response.setUserId(userId);
            
            // Overall average rating
            Double averageRating = reviewRepository.calculateAverageRating(userId);
            response.setAverageRating(averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : null);
            
            // Total reviews
            Long totalReviews = reviewRepository.countReviewsByRevieweeId(userId);
            response.setTotalReviews(totalReviews);
            
            // Driver reviews (reviews received as driver)
            // These are reviews where the user is the reviewee and reviewType is PASSENGER_TO_DRIVER
            List<Review> driverReviews = reviewRepository.findByRevieweeIdAndReviewType(
                    userId, Review.ReviewType.PASSENGER_TO_DRIVER);
            response.setDriverReviews((long) driverReviews.size());
            
            log.debug("Found {} driver reviews (PASSENGER_TO_DRIVER) for userId={}", driverReviews.size(), userId);
            
            if (!driverReviews.isEmpty()) {
                Double driverAvg = driverReviews.stream()
                        .mapToInt(Review::getRating)
                        .average()
                        .orElse(0.0);
                Double roundedDriverAvg = Math.round(driverAvg * 10.0) / 10.0;
                response.setDriverAverageRating(roundedDriverAvg);
                log.debug("✅ Calculated driver average rating: {} for userId={} from {} reviews", 
                        roundedDriverAvg, userId, driverReviews.size());
            } else {
                log.debug("No driver reviews found for userId={}", userId);
            }
            
            // Passenger reviews (reviews received as passenger)
            List<Review> passengerReviews = reviewRepository.findByRevieweeIdAndReviewType(
                    userId, Review.ReviewType.DRIVER_TO_PASSENGER);
            response.setPassengerReviews((long) passengerReviews.size());
            
            if (!passengerReviews.isEmpty()) {
                Double passengerAvg = passengerReviews.stream()
                        .mapToInt(Review::getRating)
                        .average()
                        .orElse(0.0);
                response.setPassengerAverageRating(Math.round(passengerAvg * 10.0) / 10.0);
            }
            
            log.debug("User rating response for userId={}: driverRating={}, driverReviews={}, totalReviews={}", 
                    userId, response.getDriverAverageRating(), response.getDriverReviews(), response.getTotalReviews());
            
            return response;
        } catch (Exception e) {
            log.error("Error fetching user rating for userId={}: {}", userId, e.getMessage(), e);
            // Return a response with null ratings rather than throwing
            UserRatingResponse response = new UserRatingResponse();
            response.setUserId(userId);
            return response;
        }
    }
    
    /**
     * Get reviews for a specific booking
     * @param bookingId Booking ID
     * @return List of ReviewResponse
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getBookingReviews(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        
        List<Review> reviews = reviewRepository.findByBooking(booking);
        return reviews.stream()
                .map(this::buildReviewResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if user has already reviewed a booking
     * @param bookingId Booking ID
     * @param userId User ID
     * @return true if review exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasUserReviewed(Long bookingId, Long userId) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
            
            Optional<Review> existingReview = reviewRepository.findByBookingAndReviewerId(booking, userId);
            boolean hasReviewed = existingReview.isPresent();
            
            if (hasReviewed) {
                Review review = existingReview.get();
                log.info("Review already exists: reviewId={}, bookingId={}, reviewerId={}, revieweeId={}, reviewType={}",
                        review.getId(), bookingId, userId, review.getRevieweeId(), review.getReviewType());
            } else {
                log.debug("No review found for bookingId={}, userId={}", bookingId, userId);
            }
            
            return hasReviewed;
        } catch (Exception e) {
            log.error("Error checking if user has reviewed: bookingId={}, userId={}, error={}", 
                    bookingId, userId, e.getMessage(), e);
            // Return false on error to allow review (might be table not created yet)
            return false;
        }
    }
    
    /**
     * Build ReviewResponse from Review entity
     */
    private ReviewResponse buildReviewResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setBookingId(review.getBooking().getId());
        response.setReviewerId(review.getReviewerId());
        response.setRevieweeId(review.getRevieweeId());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setReviewType(review.getReviewType());
        response.setCreatedAt(review.getCreatedAt());
        response.setUpdatedAt(review.getUpdatedAt());
        
        // Fetch reviewer and reviewee names from User Service
        try {
            Map<String, Object> reviewerProfile = userServiceClient.getUserPublicInfo(review.getReviewerId());
            if (reviewerProfile != null && reviewerProfile.get("name") != null) {
                response.setReviewerName((String) reviewerProfile.get("name"));
            }
            
            Map<String, Object> revieweeProfile = userServiceClient.getUserPublicInfo(review.getRevieweeId());
            if (revieweeProfile != null && revieweeProfile.get("name") != null) {
                response.setRevieweeName((String) revieweeProfile.get("name"));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user names for review {}: {}", review.getId(), e.getMessage());
        }
        
        return response;
    }
}
