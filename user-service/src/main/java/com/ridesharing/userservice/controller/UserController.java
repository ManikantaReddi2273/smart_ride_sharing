package com.ridesharing.userservice.controller;

import com.ridesharing.userservice.dto.*;
import com.ridesharing.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User Controller
 * Handles user profile and vehicle management endpoints
 * CORS is handled by API Gateway - no need for @CrossOrigin annotation
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * Get current user's profile
     * GET /api/users/profile
     * Requires authentication
     * 
     * @param authentication Spring Security authentication object (contains user ID)
     * @return UserProfileResponse with complete user information
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResponse response = userService.getUserProfile(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Update current user's profile
     * PUT /api/users/profile
     * Requires authentication
     * 
     * @param authentication Spring Security authentication object (contains user ID)
     * @param request Update profile request
     * @return Updated UserProfileResponse
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfileResponse response = userService.updateUserProfile(userId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Add vehicle for user
     * POST /api/users/vehicles
     * Requires authentication. Any user can add vehicles to post rides.
     * 
     * @param authentication Spring Security authentication object (contains user ID)
     * @param request Vehicle request
     * @return VehicleResponse with vehicle information
     */
    @PostMapping("/vehicles")
    public ResponseEntity<VehicleResponse> addVehicle(
            Authentication authentication,
            @Valid @RequestBody VehicleRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        VehicleResponse response = userService.addVehicle(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Get all vehicles for current user
     * GET /api/users/vehicles
     * Requires authentication. Returns all vehicles added by the user.
     * 
     * @param authentication Spring Security authentication object (contains user ID)
     * @return List of VehicleResponse
     */
    @GetMapping("/vehicles")
    public ResponseEntity<List<VehicleResponse>> getVehicles(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<VehicleResponse> vehicles = userService.getUserVehicles(userId);
        return new ResponseEntity<>(vehicles, HttpStatus.OK);
    }
    
    /**
     * Get basic user information by user ID (Public endpoint for inter-service communication)
     * GET /api/users/{userId}/public
     * Public endpoint - no authentication required
     * Used by Ride Service to fetch driver information for search results
     * 
     * @param userId User ID
     * @return Basic user information (name, email)
     */
    @GetMapping("/{userId}/public")
    public ResponseEntity<Map<String, Object>> getUserPublicInfo(@PathVariable Long userId) {
        Map<String, Object> userInfo = userService.getUserPublicInfo(userId);
        return new ResponseEntity<>(userInfo, HttpStatus.OK);
    }
    
    /**
     * Get basic vehicle information by vehicle ID (Public endpoint for inter-service communication)
     * GET /api/users/vehicles/{vehicleId}/public
     * Public endpoint - no authentication required
     * Used by Ride Service to fetch vehicle information for search results
     * 
     * @param vehicleId Vehicle ID
     * @return Basic vehicle information (model, licensePlate, color, capacity)
     */
    @GetMapping("/vehicles/{vehicleId}/public")
    public ResponseEntity<Map<String, Object>> getVehiclePublicInfo(@PathVariable Long vehicleId) {
        Map<String, Object> vehicleInfo = userService.getVehiclePublicInfo(vehicleId);
        return new ResponseEntity<>(vehicleInfo, HttpStatus.OK);
    }
}

