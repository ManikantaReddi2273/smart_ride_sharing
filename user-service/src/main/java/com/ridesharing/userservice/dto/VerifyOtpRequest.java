package com.ridesharing.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Verify OTP Request DTO
 * Carries the email and 4-digit OTP for account verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    /**
     * Registered email address used for verification.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /**
     * 4-digit OTP sent to the user's email.
     */
    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 4, message = "OTP must be 4 digits")
    @Pattern(regexp = "^[0-9]{4}$", message = "OTP must contain only digits")
    private String otp;
}

