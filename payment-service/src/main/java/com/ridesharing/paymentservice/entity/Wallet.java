package com.ridesharing.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wallet Entity
 * Represents a user's wallet for storing earnings (primarily for drivers)
 */
@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * User ID from User Service (unique - one wallet per user)
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    
    /**
     * Current wallet balance
     */
    @Column(name = "balance", nullable = false)
    private Double balance = 0.0;
    
    /**
     * Currency code (e.g., INR, USD)
     */
    @Column(name = "currency", length = 10)
    private String currency = "INR";
    
    /**
     * Timestamp when wallet was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Pre-persist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
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
