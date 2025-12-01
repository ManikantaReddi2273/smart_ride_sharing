package com.ridesharing.rideservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Review Entity
 * Represents a review/rating given by a user (driver or passenger) after ride completion
 */
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_booking_id", columnList = "booking_id"),
    @Index(name = "idx_reviewer_id", columnList = "reviewer_id"),
    @Index(name = "idx_reviewee_id", columnList = "reviewee_id"),
    @Index(name = "idx_booking_reviewer", columnList = "booking_id,reviewer_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the booking this review is for
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    
    /**
     * ID of the user who wrote the review (reviewer)
     * Can be either driver or passenger
     */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;
    
    /**
     * ID of the user being reviewed (reviewee)
     * If reviewer is driver, reviewee is passenger (and vice versa)
     */
    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;
    
    /**
     * Rating from 1 to 5 stars
     */
    @Column(name = "rating", nullable = false)
    private Integer rating;
    
    /**
     * Optional comment/review text
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    /**
     * Type of review: DRIVER_TO_PASSENGER or PASSENGER_TO_DRIVER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 30)
    private ReviewType reviewType;
    
    /**
     * Timestamp when review was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when review was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Pre-persist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Pre-update callback to set update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Review Type Enum
     */
    public enum ReviewType {
        DRIVER_TO_PASSENGER,
        PASSENGER_TO_DRIVER
    }
}
