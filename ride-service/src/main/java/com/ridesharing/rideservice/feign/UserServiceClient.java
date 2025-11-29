package com.ridesharing.rideservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Feign Client for User Service
 * Communicates with User Service to fetch user and vehicle details
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {
    
    /**
     * Get user profile by user ID
     * User ID is extracted from JWT token by User Service
     * @param authorization JWT token for authentication (contains user ID)
     * @return User profile information
     */
    @GetMapping("/api/users/profile")
    Map<String, Object> getUserProfile(
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Get vehicle details for the authenticated user
     * User ID is extracted from JWT token by User Service
     * @param authorization JWT token (contains user ID)
     * @return List of vehicles
     */
    @GetMapping("/api/users/vehicles")
    java.util.List<Map<String, Object>> getUserVehicles(
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Get basic public user information by user ID
     * Public endpoint - no authentication required
     * Used for fetching driver information in search results
     * @param userId User ID
     * @return Basic user information (id, name, email)
     */
    @GetMapping("/api/users/{userId}/public")
    Map<String, Object> getUserPublicInfo(@PathVariable("userId") Long userId);
    
    /**
     * Get basic public vehicle information by vehicle ID
     * Public endpoint - no authentication required
     * Used for fetching vehicle information in search results
     * @param vehicleId Vehicle ID
     * @return Basic vehicle information (id, model, licensePlate, color, capacity)
     */
    @GetMapping("/api/users/vehicles/{vehicleId}/public")
    Map<String, Object> getVehiclePublicInfo(@PathVariable("vehicleId") Long vehicleId);
}

