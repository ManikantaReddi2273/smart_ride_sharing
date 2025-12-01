package com.ridesharing.paymentservice.repository;

import com.ridesharing.paymentservice.entity.Withdrawal;
import com.ridesharing.paymentservice.entity.Withdrawal.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    
    /**
     * Find all withdrawals for a user
     */
    List<Withdrawal> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Find withdrawals by status
     */
    List<Withdrawal> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status);
    
    /**
     * Find withdrawals for a user by status
     */
    List<Withdrawal> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, WithdrawalStatus status);
    
    /**
     * Find withdrawals created between dates
     */
    List<Withdrawal> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        Long userId, LocalDateTime startDate, LocalDateTime endDate);
}
