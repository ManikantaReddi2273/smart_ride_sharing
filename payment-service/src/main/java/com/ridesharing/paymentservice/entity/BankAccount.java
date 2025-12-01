package com.ridesharing.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bank Account Entity
 * Stores driver's bank account details for withdrawals
 */
@Entity
@Table(name = "bank_accounts", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * User ID (driver) who owns this bank account
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * Account holder name
     */
    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;
    
    /**
     * Bank account number
     */
    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;
    
    /**
     * IFSC code
     */
    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;
    
    /**
     * Bank name
     */
    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;
    
    /**
     * Account type (SAVINGS, CURRENT)
     */
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType = "SAVINGS";
    
    /**
     * Razorpay contact ID (after creating contact in Razorpay)
     */
    @Column(name = "razorpay_contact_id", length = 255)
    private String razorpayContactId;
    
    /**
     * Razorpay fund account ID (after creating fund account in Razorpay)
     */
    @Column(name = "razorpay_fund_account_id", length = 255)
    private String razorpayFundAccountId;
    
    /**
     * Whether this is the default account for withdrawals
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;
    
    /**
     * Whether account is verified and active
     */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = Boolean.FALSE;
    
    /**
     * Timestamp when account was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when account was last updated
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
}
