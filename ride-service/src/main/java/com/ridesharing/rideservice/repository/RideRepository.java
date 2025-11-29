package com.ridesharing.rideservice.repository;

import com.ridesharing.rideservice.entity.Ride;
import com.ridesharing.rideservice.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Ride Repository
 * Data access layer for Ride entity
 */
@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    
    /**
     * Find all rides by driver ID
     * @param driverId Driver's user ID
     * @return List of rides posted by the driver
     */
    List<Ride> findByDriverId(Long driverId);
    
    /**
     * Find all rides by status
     * @param status Ride status
     * @return List of rides with the specified status
     */
    List<Ride> findByStatus(RideStatus status);
    
    /**
     * Find rides by source, destination, and date
     * Used for ride searching
     * @param source Source location
     * @param destination Destination location
     * @param rideDate Ride date
     * @param status Active status (POSTED or BOOKED)
     * @return List of matching rides
     */
    @Query("SELECT r FROM Ride r WHERE " +
           "(:source IS NULL OR LOWER(r.source) LIKE LOWER(CONCAT('%', :source, '%'))) AND " +
           "(:destination IS NULL OR LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%'))) AND " +
           "(:rideDate IS NULL OR r.rideDate = :rideDate) AND " +
           "r.status IN :status AND " +
           "r.availableSeats > 0")
    List<Ride> searchRides(
        @Param("source") String source,
        @Param("destination") String destination,
        @Param("rideDate") LocalDate rideDate,
        @Param("status") List<RideStatus> status
    );
    
    /**
     * Find rides by source (partial match)
     * @param source Source location (partial)
     * @param status Active status
     * @return List of matching rides
     */
    List<Ride> findBySourceContainingIgnoreCaseAndStatusInAndAvailableSeatsGreaterThan(
        String source, 
        List<RideStatus> status, 
        Integer availableSeats
    );
    
    /**
     * Find rides by destination (partial match)
     * @param destination Destination location (partial)
     * @param status Active status
     * @return List of matching rides
     */
    List<Ride> findByDestinationContainingIgnoreCaseAndStatusInAndAvailableSeatsGreaterThan(
        String destination, 
        List<RideStatus> status, 
        Integer availableSeats
    );
    
    /**
     * Find rides by date
     * @param rideDate Ride date
     * @param status Active status
     * @return List of matching rides
     */
    List<Ride> findByRideDateAndStatusInAndAvailableSeatsGreaterThan(
        LocalDate rideDate, 
        List<RideStatus> status, 
        Integer availableSeats
    );
}

