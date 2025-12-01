package com.ridesharing.rideservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * OTP Service for generating and validating OTPs for ride completion verification
 */
@Service
@Slf4j
public class OtpService {
    
    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 10; // OTP expires in 10 minutes
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generate a 6-digit OTP
     * @return 6-digit OTP string
     */
    public String generateOtp() {
        int otp = 100000 + random.nextInt(900000); // Generates number between 100000 and 999999
        return String.valueOf(otp);
    }
    
    /**
     * Get OTP expiration time (current time + validity period)
     * @return LocalDateTime when OTP expires
     */
    public LocalDateTime getOtpExpirationTime() {
        return LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);
    }
    
    /**
     * Validate if OTP is expired
     * @param otpExpiresAt OTP expiration timestamp
     * @return true if OTP is expired, false otherwise
     */
    public boolean isOtpExpired(LocalDateTime otpExpiresAt) {
        if (otpExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(otpExpiresAt);
    }
    
    /**
     * Validate OTP
     * @param providedOtp OTP provided by driver
     * @param storedOtp OTP stored in database
     * @param otpExpiresAt OTP expiration timestamp
     * @return true if OTP is valid, false otherwise
     */
    public boolean validateOtp(String providedOtp, String storedOtp, LocalDateTime otpExpiresAt) {
        if (providedOtp == null || storedOtp == null) {
            log.warn("OTP validation failed: null OTP provided");
            return false;
        }
        
        if (isOtpExpired(otpExpiresAt)) {
            log.warn("OTP validation failed: OTP expired. Expires at: {}, Current: {}", 
                otpExpiresAt, LocalDateTime.now());
            return false;
        }
        
        boolean isValid = providedOtp.trim().equals(storedOtp.trim());
        if (!isValid) {
            log.warn("OTP validation failed: OTP mismatch. Provided: {}, Stored: {}", 
                providedOtp, storedOtp);
        }
        
        return isValid;
    }
}
