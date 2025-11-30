package com.ridesharing.paymentservice.entity;

/**
 * Wallet Transaction Type Enumeration
 * Represents the type of wallet transaction
 */
public enum WalletTransactionType {
    /**
     * Money credited to wallet (e.g., after ride completion)
     */
    CREDIT,
    
    /**
     * Money debited from wallet (e.g., withdrawal)
     */
    DEBIT
}
