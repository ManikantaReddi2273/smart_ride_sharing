package com.ridesharing.rideservice.repository;

import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.BookingStatus;
import com.ridesharing.rideservice.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Booking Repository
 * Data access layer for Booking entity
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    /**
     * Find all bookings for a specific ride
     * @param ride Ride entity
     * @return List of bookings for the ride
     */
    List<Booking> findByRide(Ride ride);
    
    /**
     * Find all bookings by passenger ID
     * @param passengerId Passenger's user ID
     * @return List of bookings made by the passenger
     */
    List<Booking> findByPassengerId(Long passengerId);
    
    /**
     * Find all bookings by passenger ID and status
     * @param passengerId Passenger's user ID
     * @param status Booking status
     * @return List of bookings with the specified status
     */
    List<Booking> findByPassengerIdAndStatus(Long passengerId, BookingStatus status);
    
    /**
     * Find booking by ride and passenger
     * @param ride Ride entity
     * @param passengerId Passenger's user ID
     * @return Optional booking if exists
     */
    Optional<Booking> findByRideAndPassengerId(Ride ride, Long passengerId);
    
    /**
     * Count active bookings for a ride
     * @param ride Ride entity
     * @param status Active booking statuses
     * @return Count of active bookings
     */
    long countByRideAndStatusIn(Ride ride, List<BookingStatus> status);
    
    /**
     * Find all bookings by ride ID and status
     * @param rideId Ride ID
     * @param status Booking status
     * @return List of bookings with the specified status for the ride
     */
    List<Booking> findByRideIdAndStatus(Long rideId, BookingStatus status);
}

