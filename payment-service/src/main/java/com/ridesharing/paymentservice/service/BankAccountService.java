package com.ridesharing.paymentservice.service;

import com.ridesharing.paymentservice.dto.BankAccountRequest;
import com.ridesharing.paymentservice.dto.BankAccountResponse;
import com.ridesharing.paymentservice.entity.BankAccount;
import com.ridesharing.paymentservice.exception.BadRequestException;
import com.ridesharing.paymentservice.exception.ResourceNotFoundException;
import com.ridesharing.paymentservice.repository.BankAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class BankAccountService {
    
    @Autowired
    private BankAccountRepository bankAccountRepository;
    
    @Autowired
    private RazorpayService razorpayService;
    
    /**
     * Add bank account for user
     */
    public BankAccountResponse addBankAccount(Long userId, BankAccountRequest request) {
        // If this is set as default, unset other default accounts
        if (request.getIsDefault() != null && request.getIsDefault()) {
            bankAccountRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(account -> {
                    account.setIsDefault(false);
                    bankAccountRepository.save(account);
                });
        }
        
        BankAccount bankAccount = new BankAccount();
        bankAccount.setUserId(userId);
        bankAccount.setAccountHolderName(request.getAccountHolderName());
        bankAccount.setAccountNumber(request.getAccountNumber());
        bankAccount.setIfscCode(request.getIfscCode().toUpperCase());
        bankAccount.setBankName(request.getBankName());
        bankAccount.setAccountType(request.getAccountType() != null ? request.getAccountType() : "SAVINGS");
        bankAccount.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        bankAccount.setIsVerified(false);
        
        // Create Razorpay contact and fund account
        try {
            // Note: In production, get user email/phone from User Service
            String contactId = razorpayService.createContact(
                request.getAccountHolderName(),
                null, // Email - would come from User Service
                null  // Phone - would come from User Service
            );
            bankAccount.setRazorpayContactId(contactId);
            
            String fundAccountId = razorpayService.createFundAccount(
                contactId,
                request.getAccountNumber(),
                request.getIfscCode().toUpperCase(),
                request.getAccountHolderName()
            );
            bankAccount.setRazorpayFundAccountId(fundAccountId);
            bankAccount.setIsVerified(true); // Verified by Razorpay
            
            log.info("Created Razorpay contact and fund account for userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to create Razorpay contact/fund account, saving without verification: {}", e.getMessage());
            // Save account anyway, can be verified later
        }
        
        bankAccount = bankAccountRepository.save(bankAccount);
        log.info("Added bank account for userId={}, accountId={}", userId, bankAccount.getId());
        
        return toResponse(bankAccount);
    }
    
    /**
     * Get all bank accounts for user
     */
    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(Long userId) {
        return bankAccountRepository.findByUserId(userId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get bank account by ID
     */
    @Transactional(readOnly = true)
    public BankAccountResponse getBankAccount(Long accountId, Long userId) {
        BankAccount account = bankAccountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", accountId));
        
        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("Bank account does not belong to user");
        }
        
        return toResponse(account);
    }
    
    /**
     * Delete bank account
     */
    public void deleteBankAccount(Long accountId, Long userId) {
        BankAccount account = bankAccountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", accountId));
        
        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("Bank account does not belong to user");
        }
        
        bankAccountRepository.delete(account);
        log.info("Deleted bank account: accountId={}, userId={}", accountId, userId);
    }
    
    /**
     * Set default bank account
     */
    public BankAccountResponse setDefaultBankAccount(Long accountId, Long userId) {
        BankAccount account = bankAccountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "id", accountId));
        
        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("Bank account does not belong to user");
        }
        
        // Unset other default accounts
        bankAccountRepository.findByUserIdAndIsDefaultTrue(userId)
            .ifPresent(acc -> {
                acc.setIsDefault(false);
                bankAccountRepository.save(acc);
            });
        
        account.setIsDefault(true);
        account = bankAccountRepository.save(account);
        
        return toResponse(account);
    }
    
    /**
     * Convert entity to response DTO
     */
    private BankAccountResponse toResponse(BankAccount account) {
        BankAccountResponse response = new BankAccountResponse();
        response.setId(account.getId());
        response.setUserId(account.getUserId());
        response.setAccountHolderName(account.getAccountHolderName());
        // Mask account number: show only last 4 digits
        String accountNumber = account.getAccountNumber();
        if (accountNumber != null && accountNumber.length() > 4) {
            response.setAccountNumber("XXXX" + accountNumber.substring(accountNumber.length() - 4));
        } else {
            response.setAccountNumber("XXXX");
        }
        response.setIfscCode(account.getIfscCode());
        response.setBankName(account.getBankName());
        response.setAccountType(account.getAccountType());
        response.setIsDefault(account.getIsDefault());
        response.setIsVerified(account.getIsVerified());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());
        return response;
    }
}
