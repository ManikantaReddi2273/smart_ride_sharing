package com.ridesharing.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pending Registration Entity
 * Stores unverified user registration data temporarily until OTP verification
 */
@Entity
@Table(name = "pending_registrations", indexes = {
    @Index(name = "idx_pending_email", columnList = "email"),
    @Index(name = "idx_pending_phone", columnList = "phone"),
    @Index(name = "idx_pending_expires", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {
    
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
     * User's full name
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    /**
     * Encrypted password using BCrypt
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    /**
     * Verification code (OTP) sent to the user
     */
    @Column(name = "verification_code", nullable = false, length = 10)
    private String verificationCode;
    
    /**
     * Expiry timestamp for the verification code
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    /**
     * Timestamp when registration was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Pre-persist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

