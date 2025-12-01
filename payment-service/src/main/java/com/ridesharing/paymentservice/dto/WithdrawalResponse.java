package com.ridesharing.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {
    
    private Long id;
    private Long userId;
    private Long bankAccountId;
    private Double amount;
    private String currency;
    private String status;
    private String razorpayPayoutId;
    private String razorpayPayoutStatus;
    private String failureReason;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime updatedAt;
}
