package com.ridesharing.userservice.config;

import com.ridesharing.userservice.repository.PendingRegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled job to clean up expired pending registrations
 * Runs every hour to remove expired OTP registrations
 */
@Component
@Slf4j
public class PendingRegistrationCleanupJob {
    
    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;
    
    /**
     * Clean up expired pending registrations
     * Runs every hour (3600000 milliseconds)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 milliseconds
    public void cleanupExpiredRegistrations() {
        try {
            LocalDateTime now = LocalDateTime.now();
            pendingRegistrationRepository.deleteByExpiresAtBefore(now);
            log.info("Cleanup job completed: Removed expired pending registrations");
        } catch (Exception e) {
            log.error("Error during pending registration cleanup: {}", e.getMessage(), e);
        }
    }
}

