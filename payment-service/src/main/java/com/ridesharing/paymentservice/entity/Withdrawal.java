package com.ridesharing.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Withdrawal Entity
 * Represents a withdrawal request from driver wallet to bank account
 */
@Entity
@Table(name = "withdrawals", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Withdrawal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * User ID (driver) requesting withdrawal
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * Bank account ID for withdrawal
     */
    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;
    
    /**
     * Amount to withdraw (in INR)
     */
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    /**
     * Currency code
     */
    @Column(name = "currency", length = 10)
    private String currency = "INR";
    
    /**
     * Withdrawal status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawalStatus status = WithdrawalStatus.PENDING;
    
    /**
     * Razorpay payout ID (after payout is created)
     */
    @Column(name = "razorpay_payout_id", length = 255)
    private String razorpayPayoutId;
    
    /**
     * Razorpay payout status
     */
    @Column(name = "razorpay_payout_status", length = 50)
    private String razorpayPayoutStatus;
    
    /**
     * Failure reason (if withdrawal fails)
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    
    /**
     * Transaction ID (UTR) for successful withdrawals
     */
    @Column(name = "transaction_id", length = 255)
    private String transactionId;
    
    /**
     * Timestamp when withdrawal was requested
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when withdrawal was processed
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    /**
     * Timestamp when withdrawal was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Withdrawal Status Enum
     */
    public enum WithdrawalStatus {
        PENDING,        // Withdrawal requested, pending processing
        PROCESSING,      // Being processed by Razorpay
        SUCCESS,        // Successfully transferred to bank
        FAILED,         // Transfer failed
        CANCELLED       // Withdrawal cancelled
    }
}
