package com.ridesharing.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Profile Response DTO
 * Contains complete user profile information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    
    /**
     * User ID
     */
    private Long id;
    
    /**
     * User's email
     */
    private String email;
    
    /**
     * User's phone number
     */
    private String phone;
    
    /**
     * User's name
     */
    private String name;
    
    /**
     * User's role
     */
    private String role;
    
    /**
     * User account status
     */
    private String status;
    
    /**
     * Driver profile information (if user is a driver)
     */
    private DriverProfileInfo driverProfile;
    
    /**
     * Passenger profile information (if user is a passenger)
     */
    private PassengerProfileInfo passengerProfile;
    
    /**
     * List of vehicles (if user is a driver)
     */
    private List<VehicleInfo> vehicles;
    
    /**
     * Account creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Account last update timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Driver Profile Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverProfileInfo {
        private Long id;
        private String licenseNumber;
        private LocalDateTime licenseExpiryDate;
        private Boolean isVerified;
    }
    
    /**
     * Passenger Profile Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerProfileInfo {
        private Long id;
        private String preferences;
    }
    
    /**
     * Vehicle Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleInfo {
        private Long id;
        private String model;
        private String licensePlate;
        private String color;
        private Integer capacity;
        private Integer year;
        private Boolean isVerified;
    }
}

