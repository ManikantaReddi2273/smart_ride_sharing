package com.ridesharing.rideservice.service;

import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.BookingStatus;
import com.ridesharing.rideservice.entity.Ride;
import com.ridesharing.rideservice.entity.RideStatus;
import com.ridesharing.rideservice.feign.UserServiceClient;
import com.ridesharing.rideservice.repository.BookingRepository;
import com.ridesharing.rideservice.repository.RideRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending ride reminders to drivers and passengers
 * Runs on a scheduled basis to check for upcoming rides
 */
@Service
@Slf4j
public class RideReminderService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserServiceClient userServiceClient;

    @Value("${ride.reminder.hours-before:2}")
    private int hoursBefore;

    @Value("${ride.reminder.enabled:true}")
    private boolean remindersEnabled;

    // Track reminders sent to avoid duplicates (rideId + passengerId/driverId + hoursBefore)
    // Format: "rideId_userId_hoursBefore"
    private final Set<String> sentReminders = ConcurrentHashMap.newKeySet();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Scheduled task to check for upcoming rides and send reminders
     * Runs every 5 minutes to catch rides more reliably
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300000 milliseconds
    public void sendRideReminders() {
        if (!remindersEnabled) {
            log.debug("Ride reminders are disabled");
            return;
        }

        try {
            log.info("🔄 Starting ride reminder check ({} hours before ride)", hoursBefore);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime reminderTime = now.plusHours(hoursBefore);

            // Calculate time window: check rides scheduled between (hoursBefore - 15 min) and (hoursBefore + 15 min)
            // This gives us a 30-minute window to catch rides even if the task runs slightly off schedule
            LocalDateTime windowStart = reminderTime.minusMinutes(15);
            LocalDateTime windowEnd = reminderTime.plusMinutes(15);

            LocalDate startDate = windowStart.toLocalDate();
            LocalDate endDate = windowEnd.toLocalDate();
            LocalTime startTime = windowStart.toLocalTime();
            LocalTime endTime = windowEnd.toLocalTime();

            log.info("Checking for rides scheduled between {} {} and {} {}", 
                    startDate, startTime, endDate, endTime);

            // Use a Set to avoid duplicate rides
            Set<Ride> candidateRidesSet = new java.util.HashSet<>();
            
            // Check today and next 2 days to ensure we catch all relevant rides
            LocalDate today = now.toLocalDate();
            for (int i = 0; i <= 2; i++) {
                LocalDate checkDate = today.plusDays(i);
                List<Ride> ridesForDate = rideRepository.findByRideDateAndStatusInAndAvailableSeatsGreaterThan(
                        checkDate,
                        List.of(RideStatus.POSTED, RideStatus.BOOKED),
                        0
                );
                candidateRidesSet.addAll(ridesForDate);
                log.debug("Found {} rides for date {}", ridesForDate.size(), checkDate);
            }

            List<Ride> candidateRides = new java.util.ArrayList<>(candidateRidesSet);
            log.info("Found {} unique candidate rides to check (from today + 2 days)", candidateRides.size());

            int remindersSent = 0;

            for (Ride ride : candidateRides) {
                try {
                    // Combine ride date and time to get the actual ride datetime
                    LocalDateTime rideDateTime = LocalDateTime.of(ride.getRideDate(), ride.getRideTime());
                    
                    // Check if ride is within the reminder window
                    // We want to send reminders for rides that are scheduled X hours from now (with some tolerance)
                    long hoursUntilRide = java.time.Duration.between(now, rideDateTime).toHours();
                    long minutesUntilRide = java.time.Duration.between(now, rideDateTime).toMinutes();
                    
                    // Check if ride is within the reminder window (hoursBefore ± 15 minutes)
                    boolean isInWindow = minutesUntilRide >= ((hoursBefore * 60) - 15) && 
                                        minutesUntilRide <= ((hoursBefore * 60) + 15);

                    log.info("Ride {}: scheduled for {}, {} hours ({} minutes) from now, in window: {} (target: {} minutes)", 
                            ride.getId(), rideDateTime, hoursUntilRide, minutesUntilRide, isInWindow, (hoursBefore * 60));

                    if (isInWindow) {
                        log.info("📧 Sending reminders for ride {} (scheduled for {}, {} minutes from now)", 
                                ride.getId(), rideDateTime, minutesUntilRide);
                        
                        // Send reminder to driver
                        sendDriverReminder(ride);

                        // Send reminders to all confirmed passengers
                        List<Booking> confirmedBookings = bookingRepository.findByRideIdAndStatus(
                                ride.getId(),
                                BookingStatus.CONFIRMED
                        );

                        log.info("Found {} confirmed bookings for ride {}", confirmedBookings.size(), ride.getId());

                        for (Booking booking : confirmedBookings) {
                            sendPassengerReminder(ride, booking);
                        }

                        remindersSent++;
                    }
                } catch (Exception e) {
                    log.error("Error processing reminder for ride {}: {}", ride.getId(), e.getMessage(), e);
                }
            }

            log.info("✅ Ride reminder check completed. Sent {} reminder(s)", remindersSent);

            // Clean up old reminder tracking (older than 24 hours)
            cleanupOldReminders();

        } catch (Exception e) {
            log.error("Error in ride reminder scheduled task: {}", e.getMessage(), e);
        }
    }

    /**
     * Send reminder to driver
     */
    private void sendDriverReminder(Ride ride) {
        String reminderKey = String.format("%d_driver_%d_%d", ride.getId(), ride.getDriverId(), hoursBefore);
        
        // Skip if already sent
        if (sentReminders.contains(reminderKey)) {
            log.debug("Driver reminder already sent for ride {} (driver {})", ride.getId(), ride.getDriverId());
            return;
        }

        try {
            // Get driver profile
            Map<String, Object> driverProfile = userServiceClient.getUserPublicInfo(ride.getDriverId());
            String driverName = ride.getDriverName();
            String driverEmail = null;

            if (driverProfile != null) {
                if (driverName == null || driverName.isEmpty()) {
                    driverName = driverProfile.get("name") != null 
                            ? (String) driverProfile.get("name") 
                            : "Driver";
                }
                driverEmail = driverProfile.get("email") != null 
                        ? (String) driverProfile.get("email") 
                        : null;
            }

            if (driverName == null || driverName.isEmpty()) {
                driverName = "Driver";
            }

            // Count confirmed bookings
            List<Booking> confirmedBookings = bookingRepository.findByRideIdAndStatus(
                    ride.getId(),
                    BookingStatus.CONFIRMED
            );
            int bookingsCount = confirmedBookings.size();

            // Format date and time
            String rideDateStr = ride.getRideDate().format(DATE_FORMATTER);
            String rideTimeStr = ride.getRideTime().format(TIME_FORMATTER);

            // Build ride details map
            Map<String, Object> rideDetails = buildRideDetailsMap(ride);

            // Send real-time notification
            try {
                notificationService.notifyDriverRideReminder(
                        ride.getDriverId(),
                        ride.getId(),
                        rideDateStr,
                        rideTimeStr,
                        ride.getSource(),
                        ride.getDestination(),
                        bookingsCount,
                        hoursBefore
                );
                log.info("Sent real-time reminder notification to driver {} for ride {}", ride.getDriverId(), ride.getId());
            } catch (Exception e) {
                log.warn("Failed to send real-time reminder notification to driver {}: {}", 
                        ride.getDriverId(), e.getMessage());
            }

            // Send email notification
            if (driverEmail != null && !driverEmail.trim().isEmpty()) {
                try {
                    emailService.sendRideReminderToDriver(
                            driverEmail,
                            driverName,
                            rideDetails,
                            bookingsCount,
                            hoursBefore
                    );
                    log.info("Sent reminder email to driver: {}", driverEmail);
                } catch (Exception e) {
                    log.error("Failed to send reminder email to driver {}: {}", driverEmail, e.getMessage(), e);
                }
            } else {
                log.warn("Driver email not found for ride {}, cannot send reminder email", ride.getId());
            }

            // Mark as sent
            sentReminders.add(reminderKey);

        } catch (Exception e) {
            log.error("Error sending driver reminder for ride {}: {}", ride.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send reminder to passenger
     */
    private void sendPassengerReminder(Ride ride, Booking booking) {
        String reminderKey = String.format("%d_passenger_%d_%d", ride.getId(), booking.getPassengerId(), hoursBefore);
        
        // Skip if already sent
        if (sentReminders.contains(reminderKey)) {
            log.debug("Passenger reminder already sent for ride {} (passenger {})", ride.getId(), booking.getPassengerId());
            return;
        }

        try {
            // Get passenger profile
            Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
            String passengerName = passengerProfile != null && passengerProfile.get("name") != null 
                    ? (String) passengerProfile.get("name") 
                    : "Passenger";
            String passengerEmail = passengerProfile != null && passengerProfile.get("email") != null 
                    ? (String) passengerProfile.get("email") 
                    : null;

            // Get driver profile
            Map<String, Object> driverProfile = userServiceClient.getUserPublicInfo(ride.getDriverId());
            String driverName = ride.getDriverName();
            String driverEmail = null;

            if (driverProfile != null) {
                if (driverName == null || driverName.isEmpty()) {
                    driverName = driverProfile.get("name") != null 
                            ? (String) driverProfile.get("name") 
                            : "Driver";
                }
                driverEmail = driverProfile.get("email") != null 
                        ? (String) driverProfile.get("email") 
                        : null;
            }

            if (driverName == null || driverName.isEmpty()) {
                driverName = "Driver";
            }

            // Format date and time
            String rideDateStr = ride.getRideDate().format(DATE_FORMATTER);
            String rideTimeStr = ride.getRideTime().format(TIME_FORMATTER);

            // Build ride details map
            Map<String, Object> rideDetails = buildRideDetailsMap(ride);

            // Send real-time notification
            try {
                notificationService.notifyPassengerRideReminder(
                        booking.getPassengerId(),
                        ride.getId(),
                        driverName,
                        rideDateStr,
                        rideTimeStr,
                        ride.getSource(),
                        ride.getDestination(),
                        hoursBefore
                );
                log.info("Sent real-time reminder notification to passenger {} for ride {}", 
                        booking.getPassengerId(), ride.getId());
            } catch (Exception e) {
                log.warn("Failed to send real-time reminder notification to passenger {}: {}", 
                        booking.getPassengerId(), e.getMessage());
            }

            // Send email notification
            if (passengerEmail != null && !passengerEmail.trim().isEmpty()) {
                try {
                    emailService.sendRideReminderToPassenger(
                            passengerEmail,
                            passengerName,
                            driverName,
                            driverEmail,
                            rideDetails,
                            hoursBefore
                    );
                    log.info("Sent reminder email to passenger: {}", passengerEmail);
                } catch (Exception e) {
                    log.error("Failed to send reminder email to passenger {}: {}", passengerEmail, e.getMessage(), e);
                }
            } else {
                log.warn("Passenger email not found for booking {}, cannot send reminder email", booking.getId());
            }

            // Mark as sent
            sentReminders.add(reminderKey);

        } catch (Exception e) {
            log.error("Error sending passenger reminder for ride {} (booking {}): {}", 
                    ride.getId(), booking.getId(), e.getMessage(), e);
        }
    }

    /**
     * Build ride details map for email templates
     */
    private Map<String, Object> buildRideDetailsMap(Ride ride) {
        Map<String, Object> rideDetails = new HashMap<>();
        rideDetails.put("source", ride.getSource());
        rideDetails.put("destination", ride.getDestination());
        rideDetails.put("rideDate", ride.getRideDate());
        rideDetails.put("rideTime", ride.getRideTime());
        rideDetails.put("totalSeats", ride.getTotalSeats());
        rideDetails.put("availableSeats", ride.getAvailableSeats());
        rideDetails.put("vehicleModel", ride.getVehicleModel());
        rideDetails.put("vehicleLicensePlate", ride.getVehicleLicensePlate());
        rideDetails.put("vehicleColor", ride.getVehicleColor());
        rideDetails.put("distanceKm", ride.getDistanceKm());
        rideDetails.put("totalFare", ride.getTotalFare());
        rideDetails.put("currency", ride.getCurrency());
        rideDetails.put("notes", ride.getNotes());
        return rideDetails;
    }

    /**
     * Clean up old reminder tracking entries (older than 24 hours)
     * This prevents memory buildup
     */
    private void cleanupOldReminders() {
        // Simple cleanup: if set gets too large, clear it
        // In production, you might want to track timestamps and remove entries older than 24 hours
        if (sentReminders.size() > 10000) {
            log.info("Cleaning up reminder tracking set (size: {})", sentReminders.size());
            sentReminders.clear();
        }
    }

    /**
     * Manual trigger for testing purposes
     * Can be called via a REST endpoint or directly for testing
     */
    public void triggerReminderCheck() {
        log.info("🔔 Manual reminder check triggered");
        sendRideReminders();
    }
}
