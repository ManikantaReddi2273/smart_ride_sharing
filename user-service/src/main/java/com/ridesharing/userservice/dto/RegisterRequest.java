package com.ridesharing.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Register Request DTO
 * Data transfer object for user registration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    /**
     * User's email address
     * Must be a valid email format
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
    
    /**
     * User's phone number (optional)
     * Must match phone number pattern
     */
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be valid")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;
    
    /**
     * User's password
     * Must be at least 8 characters long
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
    
    /**
     * User's full name
     */
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
}

