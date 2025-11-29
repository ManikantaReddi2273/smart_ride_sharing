package com.ridesharing.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Request DTO
 * Data transfer object for user login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    /**
     * User's email or phone number for login
     */
    @NotBlank(message = "Email or phone is required")
    private String emailOrPhone;
    
    /**
     * User's password
     */
    @NotBlank(message = "Password is required")
    private String password;
}

