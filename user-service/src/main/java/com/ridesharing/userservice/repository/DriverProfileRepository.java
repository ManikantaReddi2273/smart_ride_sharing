package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * DriverProfile Repository
 * Data access layer for DriverProfile entity
 */
@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    
    /**
     * Find driver profile by user ID
     * @param userId User ID
     * @return Optional DriverProfile if found
     */
    Optional<DriverProfile> findByUserId(Long userId);
    
    /**
     * Check if driver profile exists for user
     * @param userId User ID
     * @return true if driver profile exists, false otherwise
     */
    boolean existsByUserId(Long userId);
}

