package com.ridesharing.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PassengerProfile Entity
 * Stores passenger-specific information and preferences
 */
@Entity
@Table(name = "passenger_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * One-to-One relationship with User entity
     * Each passenger profile belongs to exactly one user
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    
    /**
     * Passenger preferences (can be stored as JSON or text)
     * Example: preferred vehicle type, smoking preference, etc.
     */
    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;
    
    /**
     * Timestamp when passenger profile was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when passenger profile was last updated
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

