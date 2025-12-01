package com.ridesharing.paymentservice.service;

import com.ridesharing.paymentservice.dto.WithdrawalRequest;
import com.ridesharing.paymentservice.dto.WithdrawalResponse;
import com.ridesharing.paymentservice.entity.BankAccount;
import com.ridesharing.paymentservice.entity.Withdrawal;
import com.ridesharing.paymentservice.entity.Withdrawal.WithdrawalStatus;
import com.ridesharing.paymentservice.exception.BadRequestException;
import com.ridesharing.paymentservice.exception.ResourceNotFoundException;
import com.ridesharing.paymentservice.repository.BankAccountRepository;
import com.ridesharing.paymentservice.repository.WithdrawalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WithdrawalService {
    
    @Autowired
    private WithdrawalRepository withdrawalRepository;
    
    @Autowired
    private BankAccountRepository bankAccountRepository;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private RazorpayService razorpayService;
    
    @Value("${withdrawal.minimum.amount:100.0}")
    private Double minimumWithdrawalAmount;
    
    /**
     * Request withdrawal from wallet to bank account
     */
    public WithdrawalResponse requestWithdrawal(Long userId, WithdrawalRequest request) {
        // Validate minimum amount
        if (request.getAmount() < minimumWithdrawalAmount) {
            throw new BadRequestException("Minimum withdrawal amount is ₹" + minimumWithdrawalAmount);
        }
        
        // Check wallet balance
        Double walletBalance = walletService.getWalletBalance(userId);
        if (walletBalance < request.getAmount()) {
            throw new BadRequestException("Insufficient wallet balance. Available: ₹" + walletBalance);
        }
        
        // Get bank account
        BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", request.getBankAccountId()));
        
        if (!bankAccount.getUserId().equals(userId)) {
            throw new BadRequestException("Bank account does not belong to user");
        }
        
        if (!bankAccount.getIsVerified()) {
            throw new BadRequestException("Bank account is not verified. Please verify your bank account first.");
        }
        
        if (bankAccount.getRazorpayFundAccountId() == null || bankAccount.getRazorpayFundAccountId().isEmpty()) {
            throw new BadRequestException("Bank account is not linked to Razorpay. Please contact support.");
        }
        
        // Create withdrawal record
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUserId(userId);
        withdrawal.setBankAccountId(request.getBankAccountId());
        withdrawal.setAmount(request.getAmount());
        withdrawal.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        withdrawal.setStatus(WithdrawalStatus.PENDING);
        
        withdrawal = withdrawalRepository.save(withdrawal);
        
        // Process withdrawal asynchronously (in real app, use @Async)
        try {
            processWithdrawal(withdrawal, bankAccount);
        } catch (Exception e) {
            log.error("Failed to process withdrawal: withdrawalId={}, error={}", withdrawal.getId(), e.getMessage(), e);
            withdrawal.setStatus(WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(e.getMessage());
            withdrawal = withdrawalRepository.save(withdrawal);
        }
        
        return toResponse(withdrawal);
    }
    
    /**
     * Process withdrawal - create Razorpay payout and debit wallet
     */
    private void processWithdrawal(Withdrawal withdrawal, BankAccount bankAccount) {
        try {
            withdrawal.setStatus(WithdrawalStatus.PROCESSING);
            withdrawal = withdrawalRepository.save(withdrawal);
            
            // Convert amount to paise
            Long amountInPaise = Math.round(withdrawal.getAmount() * 100);
            
            // Create Razorpay payout
            org.json.JSONObject payout = razorpayService.createPayout(
                bankAccount.getRazorpayFundAccountId(),
                amountInPaise,
                withdrawal.getCurrency(),
                "NEFT",
                "payout",
                "Withdrawal from Smart Ride Sharing"
            );
            
            // Extract payout details
            Object payoutIdObj = payout.get("id");
            String payoutId = payoutIdObj != null ? String.valueOf(payoutIdObj) : null;
            
            Object statusObj = payout.get("status");
            String payoutStatus = statusObj != null ? String.valueOf(statusObj) : null;
            
            Object utrObj = payout.opt("utr");
            String utr = utrObj != null ? String.valueOf(utrObj) : null;
            
            withdrawal.setRazorpayPayoutId(payoutId);
            withdrawal.setRazorpayPayoutStatus(payoutStatus);
            withdrawal.setTransactionId(utr);
            
            // Debit wallet
            walletService.debitWallet(
                withdrawal.getUserId(),
                withdrawal.getAmount(),
                "Withdrawal to bank account"
            );
            
            // Update withdrawal status
            if ("processed".equalsIgnoreCase(payoutStatus) || "queued".equalsIgnoreCase(payoutStatus)) {
                withdrawal.setStatus(WithdrawalStatus.SUCCESS);
                withdrawal.setProcessedAt(LocalDateTime.now());
            } else {
                withdrawal.setStatus(WithdrawalStatus.FAILED);
                withdrawal.setFailureReason("Payout status: " + payoutStatus);
            }
            
            withdrawal = withdrawalRepository.save(withdrawal);
            log.info("Processed withdrawal: withdrawalId={}, payoutId={}, status={}", 
                withdrawal.getId(), payoutId, withdrawal.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing withdrawal: withdrawalId={}, error={}", 
                withdrawal.getId(), e.getMessage(), e);
            withdrawal.setStatus(WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(e.getMessage());
            withdrawalRepository.save(withdrawal);
            throw e;
        }
    }
    
    /**
     * Get all withdrawals for user
     */
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getWithdrawals(Long userId) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get withdrawal by ID
     */
    @Transactional(readOnly = true)
    public WithdrawalResponse getWithdrawal(Long withdrawalId, Long userId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
            .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", "id", withdrawalId));
        
        if (!withdrawal.getUserId().equals(userId)) {
            throw new BadRequestException("Withdrawal does not belong to user");
        }
        
        return toResponse(withdrawal);
    }
    
    /**
     * Convert entity to response DTO
     */
    private WithdrawalResponse toResponse(Withdrawal withdrawal) {
        WithdrawalResponse response = new WithdrawalResponse();
        response.setId(withdrawal.getId());
        response.setUserId(withdrawal.getUserId());
        response.setBankAccountId(withdrawal.getBankAccountId());
        response.setAmount(withdrawal.getAmount());
        response.setCurrency(withdrawal.getCurrency());
        response.setStatus(withdrawal.getStatus().name());
        response.setRazorpayPayoutId(withdrawal.getRazorpayPayoutId());
        response.setRazorpayPayoutStatus(withdrawal.getRazorpayPayoutStatus());
        response.setFailureReason(withdrawal.getFailureReason());
        response.setTransactionId(withdrawal.getTransactionId());
        response.setCreatedAt(withdrawal.getCreatedAt());
        response.setProcessedAt(withdrawal.getProcessedAt());
        response.setUpdatedAt(withdrawal.getUpdatedAt());
        return response;
    }
}
