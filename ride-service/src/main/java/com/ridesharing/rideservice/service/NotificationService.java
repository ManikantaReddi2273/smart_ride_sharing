package com.ridesharing.rideservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Notification Service for Real-Time Updates
 * Sends WebSocket notifications to drivers and passengers
 */
@Service
@Slf4j
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Notify driver about a new booking
     * @param driverId Driver's user ID
     * @param bookingId Booking ID
     * @param passengerName Passenger's name
     * @param seatsBooked Number of seats booked
     * @param rideId Ride ID
     */
    public void notifyDriverNewBooking(Long driverId, Long bookingId, String passengerName, 
                                      Integer seatsBooked, Long rideId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_BOOKING");
        notification.put("message", String.format("%s booked %d seat(s) on your ride", passengerName, seatsBooked));
        notification.put("bookingId", bookingId);
        notification.put("rideId", rideId);
        notification.put("passengerName", passengerName);
        notification.put("seatsBooked", seatsBooked);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/driver/" + driverId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent new booking notification to driver {}: {}", driverId, notification);
    }

    /**
     * Notify passenger about booking confirmation
     * @param passengerId Passenger's user ID
     * @param bookingId Booking ID
     * @param rideId Ride ID
     * @param driverName Driver's name
     */
    public void notifyPassengerBookingConfirmed(Long passengerId, Long bookingId, Long rideId, String driverName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BOOKING_CONFIRMED");
        notification.put("message", String.format("Your booking with %s has been confirmed", driverName));
        notification.put("bookingId", bookingId);
        notification.put("rideId", rideId);
        notification.put("driverName", driverName);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/passenger/" + passengerId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent booking confirmation notification to passenger {}: {}", passengerId, notification);
    }

    /**
     * Notify passenger about ride cancellation
     * @param passengerId Passenger's user ID
     * @param rideId Ride ID
     * @param driverName Driver's name
     * @param reason Cancellation reason (optional)
     */
    public void notifyPassengerRideCancelled(Long passengerId, Long rideId, String driverName, String reason) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_CANCELLED");
        notification.put("message", String.format("%s cancelled the ride", driverName));
        notification.put("rideId", rideId);
        notification.put("driverName", driverName);
        notification.put("reason", reason);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/passenger/" + passengerId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent ride cancellation notification to passenger {}: {}", passengerId, notification);
    }

    /**
     * Notify passenger about ride reschedule
     * @param passengerId Passenger's user ID
     * @param rideId Ride ID
     * @param driverName Driver's name
     * @param newDate New ride date
     * @param newTime New ride time
     */
    public void notifyPassengerRideRescheduled(Long passengerId, Long rideId, String driverName, 
                                              String newDate, String newTime) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_RESCHEDULED");
        notification.put("message", String.format("%s rescheduled the ride to %s at %s", driverName, newDate, newTime));
        notification.put("rideId", rideId);
        notification.put("driverName", driverName);
        notification.put("newDate", newDate);
        notification.put("newTime", newTime);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/passenger/" + passengerId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent ride reschedule notification to passenger {}: {}", passengerId, notification);
    }

    /**
     * Notify driver about booking cancellation
     * @param driverId Driver's user ID
     * @param bookingId Booking ID
     * @param passengerName Passenger's name
     * @param rideId Ride ID
     */
    public void notifyDriverBookingCancelled(Long driverId, Long bookingId, String passengerName, Long rideId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BOOKING_CANCELLED");
        notification.put("message", String.format("%s cancelled their booking", passengerName));
        notification.put("bookingId", bookingId);
        notification.put("rideId", rideId);
        notification.put("passengerName", passengerName);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/driver/" + driverId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent booking cancellation notification to driver {}: {}", driverId, notification);
    }

    /**
     * Notify driver about ride completion request (OTP sent)
     * @param driverId Driver's user ID
     * @param bookingId Booking ID
     * @param passengerName Passenger's name
     */
    public void notifyDriverRideCompletionRequest(Long driverId, Long bookingId, String passengerName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_COMPLETION_REQUEST");
        notification.put("message", String.format("OTP sent to %s for ride completion", passengerName));
        notification.put("bookingId", bookingId);
        notification.put("passengerName", passengerName);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/driver/" + driverId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent ride completion request notification to driver {}: {}", driverId, notification);
    }

    /**
     * Notify passenger about ride completion (OTP received)
     * @param passengerId Passenger's user ID
     * @param bookingId Booking ID
     * @param driverName Driver's name
     */
    public void notifyPassengerRideCompleted(Long passengerId, Long bookingId, String driverName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_COMPLETED");
        notification.put("message", String.format("Ride with %s completed. Please verify OTP to complete payment.", driverName));
        notification.put("bookingId", bookingId);
        notification.put("driverName", driverName);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destination = "/topic/passenger/" + passengerId;
        messagingTemplate.convertAndSend(destination, notification);
        
        log.info("✅ Sent ride completion notification to passenger {}: {}", passengerId, notification);
    }

    /**
     * Notify passenger about upcoming ride reminder
     * @param passengerId Passenger's user ID
     * @param rideId Ride ID
     * @param driverName Driver's name
     * @param rideDate Ride date
     * @param rideTime Ride time
     * @param source Source location
     * @param destination Destination location
     * @param hoursBefore Hours before the ride
     */
    public void notifyPassengerRideReminder(Long passengerId, Long rideId, String driverName,
                                            String rideDate, String rideTime, String source,
                                            String destination, int hoursBefore) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_REMINDER");
        notification.put("message", String.format("Reminder: Your ride with %s is in %d hour(s) - %s to %s on %s at %s",
                driverName, hoursBefore, source, destination, rideDate, rideTime));
        notification.put("rideId", rideId);
        notification.put("driverName", driverName);
        notification.put("rideDate", rideDate);
        notification.put("rideTime", rideTime);
        notification.put("source", source);
        notification.put("destination", destination);
        notification.put("hoursBefore", hoursBefore);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destinationTopic = "/topic/passenger/" + passengerId;
        messagingTemplate.convertAndSend(destinationTopic, notification);
        
        log.info("✅ Sent ride reminder notification to passenger {}: {}", passengerId, notification);
    }

    /**
     * Notify driver about upcoming ride reminder
     * @param driverId Driver's user ID
     * @param rideId Ride ID
     * @param rideDate Ride date
     * @param rideTime Ride time
     * @param source Source location
     * @param destination Destination location
     * @param bookingsCount Number of confirmed bookings
     * @param hoursBefore Hours before the ride
     */
    public void notifyDriverRideReminder(Long driverId, Long rideId, String rideDate, String rideTime,
                                        String source, String destination, int bookingsCount, int hoursBefore) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_REMINDER");
        notification.put("message", String.format("Reminder: Your ride from %s to %s is in %d hour(s) - %s at %s (%d booking(s))",
                source, destination, hoursBefore, rideDate, rideTime, bookingsCount));
        notification.put("rideId", rideId);
        notification.put("rideDate", rideDate);
        notification.put("rideTime", rideTime);
        notification.put("source", source);
        notification.put("destination", destination);
        notification.put("bookingsCount", bookingsCount);
        notification.put("hoursBefore", hoursBefore);
        notification.put("timestamp", System.currentTimeMillis());
        
        String destinationTopic = "/topic/driver/" + driverId;
        messagingTemplate.convertAndSend(destinationTopic, notification);
        
        log.info("✅ Sent ride reminder notification to driver {}: {}", driverId, notification);
    }

    /**
     * Send generic notification to a user
     * @param userId User ID
     * @param type Notification type
     * @param message Notification message
     * @param data Additional data
     */
    public void sendNotification(Long userId, String type, String message, Map<String, Object> data) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("message", message);
        notification.put("timestamp", System.currentTimeMillis());
        
        if (data != null) {
            notification.putAll(data);
        }
        
        // Try both driver and passenger topics
        String driverDestination = "/topic/driver/" + userId;
        String passengerDestination = "/topic/passenger/" + userId;
        
        messagingTemplate.convertAndSend(driverDestination, notification);
        messagingTemplate.convertAndSend(passengerDestination, notification);
        
        log.info("✅ Sent generic notification to user {}: {}", userId, notification);
    }
}
