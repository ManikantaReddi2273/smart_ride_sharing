package com.ridesharing.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wallet Transaction Entity
 * Represents a transaction in a user's wallet
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
    @Index(name = "idx_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_payment_id", columnList = "payment_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the wallet
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    /**
     * Type of transaction (CREDIT or DEBIT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WalletTransactionType type;
    
    /**
     * Transaction amount
     */
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    /**
     * Wallet balance after this transaction
     */
    @Column(name = "balance_after", nullable = false)
    private Double balanceAfter;
    
    /**
     * Description of the transaction
     */
    @Column(name = "description", length = 500)
    private String description;
    
    /**
     * Reference to payment (if transaction is related to a payment)
     */
    @Column(name = "payment_id")
    private Long paymentId;
    
    /**
     * Timestamp when transaction was created
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
