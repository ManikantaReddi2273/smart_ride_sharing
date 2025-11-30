package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Wallet Repository
 * Data access layer for Wallet entity
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    /**
     * Find wallet by user ID
     * @param userId User ID
     * @return Optional wallet if exists
     */
    Optional<Wallet> findByUserId(Long userId);
}
