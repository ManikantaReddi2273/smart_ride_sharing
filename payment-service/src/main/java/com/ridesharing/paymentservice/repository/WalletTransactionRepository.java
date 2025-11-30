package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.Wallet;
import com.ridesharing.paymentservice.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Wallet Transaction Repository
 * Data access layer for WalletTransaction entity
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    
    /**
     * Find all transactions for a wallet
     * @param wallet Wallet entity
     * @return List of wallet transactions
     */
    List<WalletTransaction> findByWalletOrderByCreatedAtDesc(Wallet wallet);
    
    /**
     * Find all transactions by payment ID
     * @param paymentId Payment ID
     * @return List of wallet transactions related to the payment
     */
    List<WalletTransaction> findByPaymentId(Long paymentId);
}
