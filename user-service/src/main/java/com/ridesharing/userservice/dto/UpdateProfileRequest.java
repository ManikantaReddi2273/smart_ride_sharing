package com.ridesharing.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update Profile Request DTO
 * Data transfer object for updating user profile
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    
    /**
     * User's name (optional update)
     */
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    /**
     * User's phone number (optional update)
     */
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be valid")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;
    
    /**
     * User's email (optional update)
     */
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
}

