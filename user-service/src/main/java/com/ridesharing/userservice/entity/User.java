package com.ridesharing.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Entity
 * Represents a user account in the system (can be Driver or Passenger)
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * User's email address (unique identifier)
     */
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;
    
    /**
     * User's phone number (unique identifier)
     */
    @Column(name = "phone", unique = true, length = 20)
    private String phone;
    
    /**
     * Encrypted password using BCrypt
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    /**
     * User's full name
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    /**
     * User's role (DRIVER or PASSENGER)
     * Many-to-One relationship with Role entity
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    /**
     * User account status (ACTIVE, INACTIVE, BLOCKED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Flag indicating if the user has verified their email via OTP
     */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = Boolean.FALSE;

    /**
     * Latest verification code (OTP) sent to the user
     */
    @Column(name = "verification_code", length = 10)
    private String verificationCode;

    /**
     * Expiry timestamp for the verification code
     */
    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;
    
    /**
     * Timestamp when user account was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when user account was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * One-to-One relationship with DriverProfile (if user is a driver)
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DriverProfile driverProfile;
    
    /**
     * One-to-One relationship with PassengerProfile (if user is a passenger)
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PassengerProfile passengerProfile;
    
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
     * User Status Enumeration
     */
    public enum UserStatus {
        ACTIVE,      // User account is active
        INACTIVE,    // User account is inactive
        BLOCKED      // User account is blocked by admin
    }
}

