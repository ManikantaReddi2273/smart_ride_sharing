package com.ridesharing.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DriverProfile Entity
 * Stores driver-specific information and vehicle details
 */
@Entity
@Table(name = "driver_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * One-to-One relationship with User entity
     * Each driver profile belongs to exactly one user
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    
    /**
     * Driver's license number
     */
    @Column(name = "license_number", unique = true, length = 50)
    private String licenseNumber;
    
    /**
     * Driver's license expiry date
     */
    @Column(name = "license_expiry_date")
    private LocalDateTime licenseExpiryDate;
    
    /**
     * Driver verification status
     */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;
    
    /**
     * Timestamp when driver profile was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when driver profile was last updated
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

