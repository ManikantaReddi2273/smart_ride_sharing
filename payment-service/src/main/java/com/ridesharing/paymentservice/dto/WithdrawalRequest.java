package com.ridesharing.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {
    
    @NotNull(message = "Bank account ID is required")
    private Long bankAccountId;
    
    @NotNull(message = "Amount is required")
    @Min(value = 100, message = "Minimum withdrawal amount is ₹100")
    private Double amount;
    
    private String currency = "INR";
}
