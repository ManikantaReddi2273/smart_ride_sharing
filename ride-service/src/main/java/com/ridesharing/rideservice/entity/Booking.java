package com.ridesharing.rideservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Booking Entity
 * Represents a seat booking by a passenger
 */
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_ride_id", columnList = "ride_id"),
    @Index(name = "idx_passenger_id", columnList = "passenger_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the ride being booked
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;
    
    /**
     * Passenger's user ID (from User Service)
     * Not a foreign key - stored as reference
     */
    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;
    
    /**
     * Number of seats booked
     */
    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked = 1;
    
    /**
     * Current status of the booking
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    /**
     * Passenger's source location (nullable).
     * If null, passenger is assumed to start at the ride source.
     */
    @Column(name = "passenger_source", length = 255)
    private String passengerSource;

    /**
     * Passenger's destination location (nullable).
     * If null, passenger is assumed to end at the ride destination.
     */
    @Column(name = "passenger_destination", length = 255)
    private String passengerDestination;

    /**
     * Distance for this passenger's specific journey in kilometers.
     */
    @Column(name = "passenger_distance_km")
    private Double passengerDistanceKm;

    /**
     * Fare charged to this passenger for their journey.
     */
    @Column(name = "passenger_fare")
    private Double passengerFare;

    /**
     * Currency code for passenger fare (e.g., INR, USD).
     */
    @Column(name = "currency", length = 10)
    private String currency;
    
    /**
     * Timestamp when booking was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when booking was last updated
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
}

