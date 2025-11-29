package com.ridesharing.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vehicle Entity
 * Represents a vehicle owned by a driver
 */
@Entity
@Table(name = "vehicles", indexes = {
    @Index(name = "idx_driver_id", columnList = "driver_id"),
    @Index(name = "idx_license_plate", columnList = "license_plate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Many-to-One relationship with User (driver)
     * A driver can have multiple vehicles
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;
    
    /**
     * Vehicle model/make (e.g., "Toyota Camry", "Honda Civic")
     */
    @Column(name = "model", nullable = false, length = 100)
    private String model;
    
    /**
     * Vehicle license plate number (unique identifier)
     */
    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;
    
    /**
     * Vehicle color
     */
    @Column(name = "color", length = 50)
    private String color;
    
    /**
     * Maximum seating capacity (including driver)
     */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;
    
    /**
     * Vehicle year
     */
    @Column(name = "year")
    private Integer year;
    
    /**
     * Vehicle verification status
     */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;
    
    /**
     * Timestamp when vehicle was registered
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when vehicle information was last updated
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

