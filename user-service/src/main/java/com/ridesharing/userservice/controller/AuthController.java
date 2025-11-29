package com.ridesharing.userservice.controller;

import com.ridesharing.userservice.dto.ApiResponse;
import com.ridesharing.userservice.dto.AuthResponse;
import com.ridesharing.userservice.dto.LoginRequest;
import com.ridesharing.userservice.dto.RegisterRequest;
import com.ridesharing.userservice.dto.VerifyOtpRequest;
import com.ridesharing.userservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles user registration and login endpoints
 * CORS is handled by API Gateway - no need for @CrossOrigin annotation
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    /**
     * Register a new user
     * POST /api/users/register
     * 
     * @param request Registration request containing user details
     * @return AuthResponse with JWT token and user information
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Login user
     * POST /api/users/login
     * 
     * @param request Login request containing email/phone and password
     * @return AuthResponse with JWT token and user information
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Verify OTP sent to user's email after registration.
     * POST /api/users/verify-otp
     *
     * @param request VerifyOtpRequest containing email and OTP
     * @return ApiResponse with verification result
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        ApiResponse response = authService.verifyOtp(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

