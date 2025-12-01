package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    
    /**
     * Find all bank accounts for a user
     */
    List<BankAccount> findByUserId(Long userId);
    
    /**
     * Find default bank account for a user
     */
    Optional<BankAccount> findByUserIdAndIsDefaultTrue(Long userId);
    
    /**
     * Find verified bank accounts for a user
     */
    List<BankAccount> findByUserIdAndIsVerifiedTrue(Long userId);
    
    /**
     * Check if user has any bank account
     */
    boolean existsByUserId(Long userId);
}
