package com.ridesharing.rideservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ride Entity
 * Represents a ride posted by a driver
 */
@Entity
@Table(name = "rides", indexes = {
    @Index(name = "idx_driver_id", columnList = "driver_id"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_destination", columnList = "destination"),
    @Index(name = "idx_date", columnList = "ride_date"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ride {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Driver's user ID (from User Service)
     * Not a foreign key - stored as reference
     */
    @Column(name = "driver_id", nullable = false)
    private Long driverId;
    
    /**
     * Vehicle ID (from User Service)
     * References the vehicle being used for this ride
     */
    @Column(name = "vehicle_id")
    private Long vehicleId;
    
    /**
     * Source location of the ride
     */
    @Column(name = "source", nullable = false, length = 255)
    private String source;
    
    /**
     * Destination location of the ride
     */
    @Column(name = "destination", nullable = false, length = 255)
    private String destination;
    
    /**
     * Date of the ride
     */
    @Column(name = "ride_date", nullable = false)
    private LocalDate rideDate;
    
    /**
     * Time of the ride
     */
    @Column(name = "ride_time", nullable = false)
    private LocalTime rideTime;
    
    /**
     * Total number of seats available (including driver)
     */
    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;
    
    /**
     * Number of seats currently available
     */
    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;
    
    /**
     * Current status of the ride
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RideStatus status = RideStatus.POSTED;
    
    /**
     * Additional notes or instructions from driver
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    /**
     * Driver name (denormalized for search results)
     * Stored when ride is posted to avoid fetching from User Service
     */
    @Column(name = "driver_name", length = 100)
    private String driverName;
    
    /**
     * Vehicle model (denormalized for search results)
     * Stored when ride is posted to avoid fetching from User Service
     */
    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;
    
    /**
     * Vehicle license plate (denormalized for search results)
     */
    @Column(name = "vehicle_license_plate", length = 20)
    private String vehicleLicensePlate;
    
    /**
     * Vehicle color (denormalized for search results)
     */
    @Column(name = "vehicle_color", length = 50)
    private String vehicleColor;
    
    /**
     * Vehicle capacity (denormalized for search results)
     */
    @Column(name = "vehicle_capacity")
    private Integer vehicleCapacity;

    /**
     * Total distance for this ride in kilometers.
     * Calculated using Google Distance Matrix API.
     */
    @Column(name = "distance_km")
    private Double distanceKm;

    /**
     * Total fare for this ride (full route) in configured currency.
     * Formula: baseFare + (ratePerKm * distanceKm)
     */
    @Column(name = "total_fare")
    private Double totalFare;

    /**
     * Base fare amount used when calculating total fare.
     */
    @Column(name = "base_fare")
    private Double baseFare;

    /**
     * Rate per kilometer used when calculating total fare.
     */
    @Column(name = "rate_per_km")
    private Double ratePerKm;

    /**
     * Currency code for fare values (e.g., INR, USD).
     */
    @Column(name = "currency", length = 10)
    private String currency;
    
    /**
     * Route geometry (polyline) as JSON array of [longitude, latitude] coordinates.
     * Stored when ride is posted to enable partial route matching.
     * Format: [[lon1, lat1], [lon2, lat2], ...]
     * 
     * CRITICAL: Using TEXT/CLOB to ensure full polyline JSON is stored (can be several KB).
     * VARCHAR with default length would truncate large geometries.
     */
    @Column(name = "route_geometry", columnDefinition = "TEXT")
    private String routeGeometry;
    
    /**
     * Timestamp when ride was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when ride was last updated
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
        // Initialize available seats to total seats if not set
        if (availableSeats == null && totalSeats != null) {
            availableSeats = totalSeats;
        }
    }
    
    /**
     * Pre-update callback to set update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

