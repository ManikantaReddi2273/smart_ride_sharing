package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Vehicle Repository
 * Data access layer for Vehicle entity
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    
    /**
     * Find all vehicles by driver ID
     * @param driverId Driver's user ID
     * @return List of vehicles owned by the driver
     */
    List<Vehicle> findByDriverId(Long driverId);
    
    /**
     * Find vehicle by license plate
     * @param licensePlate Vehicle license plate number
     * @return Optional Vehicle if found
     */
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    
    /**
     * Check if vehicle exists by license plate
     * @param licensePlate Vehicle license plate number
     * @return true if vehicle exists, false otherwise
     */
    boolean existsByLicensePlate(String licensePlate);
}

