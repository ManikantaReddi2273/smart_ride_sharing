package com.ridesharing.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {
    
    private Long id;
    private Long userId;
    private String accountHolderName;
    private String accountNumber; // Masked: XXXX1234
    private String ifscCode;
    private String bankName;
    private String accountType;
    private Boolean isDefault;
    private Boolean isVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
