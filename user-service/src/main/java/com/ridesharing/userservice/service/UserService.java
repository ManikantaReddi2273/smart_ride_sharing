package com.ridesharing.userservice.service;

import com.ridesharing.userservice.dto.*;
import com.ridesharing.userservice.entity.*;
import com.ridesharing.userservice.exception.BadRequestException;
import com.ridesharing.userservice.exception.ResourceNotFoundException;
import com.ridesharing.userservice.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User Service
 * Handles user profile and vehicle management business logic
 */
@Service
@Transactional
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DriverProfileRepository driverProfileRepository;
    
    @Autowired
    private PassengerProfileRepository passengerProfileRepository;
    
    @Autowired
    private VehicleRepository vehicleRepository;
    
    /**
     * Get user profile by user ID
     * @param userId User ID
     * @return UserProfileResponse with complete user information
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        return buildUserProfileResponse(user);
    }
    
    /**
     * Update user profile
     * @param userId User ID
     * @param request Update profile request
     * @return Updated UserProfileResponse
     */
    public UserProfileResponse updateUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Update name if provided
        if (request.getName() != null && !request.getName().isEmpty()) {
            user.setName(request.getName());
        }
        
        // Update phone if provided
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            // Check if phone is already taken by another user
            if (userRepository.existsByPhone(request.getPhone()) && 
                !user.getPhone().equals(request.getPhone())) {
                throw new BadRequestException("Phone number already exists");
            }
            user.setPhone(request.getPhone());
        }
        
        // Update email if provided
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            // Check if email is already taken by another user
            if (userRepository.existsByEmail(request.getEmail()) && 
                !user.getEmail().equals(request.getEmail())) {
                throw new BadRequestException("Email already exists");
            }
            user.setEmail(request.getEmail());
        }
        
        user = userRepository.save(user);
        return buildUserProfileResponse(user);
    }
    
    /**
     * Add vehicle for user
     * Any authenticated user can add vehicles to post rides
     * @param userId User ID
     * @param request Vehicle request
     * @return VehicleResponse with vehicle information
     */
    public VehicleResponse addVehicle(Long userId, VehicleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Any user can add vehicles to post rides
        // Check if license plate already exists
        if (vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new BadRequestException("Vehicle with this license plate already exists");
        }
        
        // Create new vehicle
        Vehicle vehicle = new Vehicle();
        vehicle.setDriver(user);
        vehicle.setModel(request.getModel());
        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setColor(request.getColor());
        vehicle.setCapacity(request.getCapacity());
        vehicle.setYear(request.getYear());
        vehicle.setIsVerified(false);
        
        vehicle = vehicleRepository.save(vehicle);
        
        return buildVehicleResponse(vehicle);
    }
    
    /**
     * Get all vehicles for a user
     * Any authenticated user can have vehicles to post rides
     * @param userId User ID
     * @return List of VehicleResponse
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> getUserVehicles(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Any user can have vehicles to post rides
        List<Vehicle> vehicles = vehicleRepository.findByDriverId(userId);
        return vehicles.stream()
                .map(this::buildVehicleResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get basic public user information by user ID
     * Public endpoint for inter-service communication
     * @param userId User ID
     * @return Map with basic user information (id, name, email)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserPublicInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        return Map.of(
            "id", user.getId(),
            "name", user.getName() != null ? user.getName() : "",
            "email", user.getEmail() != null ? user.getEmail() : ""
        );
    }
    
    /**
     * Get basic public vehicle information by vehicle ID
     * Public endpoint for inter-service communication
     * @param vehicleId Vehicle ID
     * @return Map with basic vehicle information (id, model, licensePlate, color, capacity)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVehiclePublicInfo(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", vehicleId));
        
        return Map.of(
            "id", vehicle.getId(),
            "model", vehicle.getModel() != null ? vehicle.getModel() : "",
            "licensePlate", vehicle.getLicensePlate() != null ? vehicle.getLicensePlate() : "",
            "color", vehicle.getColor() != null ? vehicle.getColor() : "",
            "capacity", vehicle.getCapacity() != null ? vehicle.getCapacity() : 0
        );
    }
    
    /**
     * Build UserProfileResponse from User entity
     * @param user User entity
     * @return UserProfileResponse
     */
    private UserProfileResponse buildUserProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setName(user.getName());
        response.setRole(user.getRole().getName());
        response.setStatus(user.getStatus().name());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        
        // Add driver profile if user is a driver
        if ("DRIVER".equals(user.getRole().getName())) {
            driverProfileRepository.findByUserId(user.getId()).ifPresent(driverProfile -> {
                UserProfileResponse.DriverProfileInfo driverInfo = 
                    new UserProfileResponse.DriverProfileInfo(
                        driverProfile.getId(),
                        driverProfile.getLicenseNumber(),
                        driverProfile.getLicenseExpiryDate(),
                        driverProfile.getIsVerified()
                    );
                response.setDriverProfile(driverInfo);
            });
            
            // Add vehicles
            List<Vehicle> vehicles = vehicleRepository.findByDriverId(user.getId());
            List<UserProfileResponse.VehicleInfo> vehicleInfos = vehicles.stream()
                    .map(v -> new UserProfileResponse.VehicleInfo(
                        v.getId(),
                        v.getModel(),
                        v.getLicensePlate(),
                        v.getColor(),
                        v.getCapacity(),
                        v.getYear(),
                        v.getIsVerified()
                    ))
                    .collect(Collectors.toList());
            response.setVehicles(vehicleInfos);
        }
        
        // Add passenger profile if user is a passenger
        if ("PASSENGER".equals(user.getRole().getName())) {
            passengerProfileRepository.findByUserId(user.getId()).ifPresent(passengerProfile -> {
                UserProfileResponse.PassengerProfileInfo passengerInfo = 
                    new UserProfileResponse.PassengerProfileInfo(
                        passengerProfile.getId(),
                        passengerProfile.getPreferences()
                    );
                response.setPassengerProfile(passengerInfo);
            });
        }
        
        return response;
    }
    
    /**
     * Build VehicleResponse from Vehicle entity
     * @param vehicle Vehicle entity
     * @return VehicleResponse
     */
    private VehicleResponse buildVehicleResponse(Vehicle vehicle) {
        VehicleResponse response = new VehicleResponse();
        response.setId(vehicle.getId());
        response.setModel(vehicle.getModel());
        response.setLicensePlate(vehicle.getLicensePlate());
        response.setColor(vehicle.getColor());
        response.setCapacity(vehicle.getCapacity());
        response.setYear(vehicle.getYear());
        response.setIsVerified(vehicle.getIsVerified());
        response.setCreatedAt(vehicle.getCreatedAt());
        response.setUpdatedAt(vehicle.getUpdatedAt());
        return response;
    }
}

