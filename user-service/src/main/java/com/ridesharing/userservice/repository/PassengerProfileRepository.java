package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.PassengerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PassengerProfile Repository
 * Data access layer for PassengerProfile entity
 */
@Repository
public interface PassengerProfileRepository extends JpaRepository<PassengerProfile, Long> {
    
    /**
     * Find passenger profile by user ID
     * @param userId User ID
     * @return Optional PassengerProfile if found
     */
    Optional<PassengerProfile> findByUserId(Long userId);
    
    /**
     * Check if passenger profile exists for user
     * @param userId User ID
     * @return true if passenger profile exists, false otherwise
     */
    boolean existsByUserId(Long userId);
}

