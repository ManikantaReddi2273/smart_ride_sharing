package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Pending Registration Repository
 * Data access layer for PendingRegistration entity
 */
@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    
    /**
     * Find pending registration by email
     * @param email User's email address
     * @return Optional PendingRegistration if found
     */
    Optional<PendingRegistration> findByEmail(String email);
    
    /**
     * Check if pending registration exists by email or phone
     * @param email User's email address
     * @param phone User's phone number
     * @return true if pending registration exists, false otherwise
     */
    @Query("SELECT COUNT(p) > 0 FROM PendingRegistration p WHERE p.email = :email OR p.phone = :phone")
    boolean existsByEmailOrPhone(@Param("email") String email, @Param("phone") String phone);
    
    /**
     * Delete expired pending registrations
     * Used for cleanup of expired OTP registrations
     * @param now Current timestamp
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :now")
    void deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}

