package com.ridesharing.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication Response DTO
 * Contains JWT token and user information after successful login/registration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    /**
     * JWT token for authentication
     */
    private String token;
    
    /**
     * Token type (usually "Bearer")
     */
    private String tokenType = "Bearer";
    
    /**
     * User ID
     */
    private Long userId;
    
    /**
     * User's email
     */
    private String email;
    
    /**
     * User's name
     */
    private String name;
    
    /**
     * User's role (DRIVER or PASSENGER)
     */
    private String role;
    
    /**
     * Token expiration time in milliseconds
     */
    private Long expiresIn;

    /**
     * Flag to indicate if additional verification is required (e.g., OTP)
     */
    private Boolean verificationRequired = Boolean.FALSE;

    /**
     * Informational message for the client (OTP instructions, etc.)
     */
    private String message;
}

