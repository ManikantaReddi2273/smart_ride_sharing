package com.ridesharing.paymentservice.service;

import com.ridesharing.paymentservice.entity.Payment;
import com.ridesharing.paymentservice.entity.Wallet;
import com.ridesharing.paymentservice.entity.WalletTransaction;
import com.ridesharing.paymentservice.entity.WalletTransactionType;
import com.ridesharing.paymentservice.exception.BadRequestException;
import com.ridesharing.paymentservice.exception.ResourceNotFoundException;
import com.ridesharing.paymentservice.repository.WalletRepository;
import com.ridesharing.paymentservice.repository.WalletTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Wallet Service
 * Handles wallet operations (credit, debit, balance)
 */
@Service
@Slf4j
@Transactional
public class WalletService {
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    
    /**
     * Get or create wallet for user (write operation)
     * @param userId User ID
     * @return Wallet entity
     */
    public Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
            .orElseGet(() -> {
                Wallet wallet = new Wallet();
                wallet.setUserId(userId);
                wallet.setBalance(0.0);
                wallet.setCurrency("INR");
                log.info("Creating new wallet for userId={}", userId);
                return walletRepository.save(wallet);
            });
    }
    
    /**
     * Get wallet (read-only, returns null if not exists)
     * @param userId User ID
     * @return Wallet entity or null
     */
    @Transactional(readOnly = true)
    public Wallet getWallet(Long userId) {
        return walletRepository.findByUserId(userId).orElse(null);
    }
    
    /**
     * Get wallet balance
     * @param userId User ID
     * @return Wallet balance (0.0 if wallet doesn't exist)
     */
    @Transactional(readOnly = true)
    public Double getWalletBalance(Long userId) {
        Wallet wallet = getWallet(userId);
        return wallet != null ? wallet.getBalance() : 0.0;
    }
    
    /**
     * Credit amount to wallet (e.g., after ride completion)
     * @param userId User ID
     * @param amount Amount to credit
     * @param description Transaction description
     * @param paymentId Payment ID (if related to a payment)
     * @return Updated wallet
     */
    public Wallet creditWallet(Long userId, Double amount, String description, Long paymentId) {
        if (amount <= 0) {
            throw new BadRequestException("Credit amount must be greater than 0");
        }
        
        Wallet wallet = getOrCreateWallet(userId);
        Double newBalance = wallet.getBalance() + amount;
        wallet.setBalance(newBalance);
        wallet = walletRepository.save(wallet);
        
        // Create transaction record
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(WalletTransactionType.CREDIT);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(description);
        transaction.setPaymentId(paymentId);
        walletTransactionRepository.save(transaction);
        
        log.info("Credited {} {} to wallet userId={}, new balance={}", 
            amount, wallet.getCurrency(), userId, newBalance);
        
        return wallet;
    }
    
    /**
     * Debit amount from wallet (e.g., withdrawal)
     * @param userId User ID
     * @param amount Amount to debit
     * @param description Transaction description
     * @return Updated wallet
     */
    public Wallet debitWallet(Long userId, Double amount, String description) {
        if (amount <= 0) {
            throw new BadRequestException("Debit amount must be greater than 0");
        }
        
        Wallet wallet = getOrCreateWallet(userId);
        
        if (wallet.getBalance() < amount) {
            throw new BadRequestException("Insufficient wallet balance");
        }
        
        Double newBalance = wallet.getBalance() - amount;
        wallet.setBalance(newBalance);
        wallet = walletRepository.save(wallet);
        
        // Create transaction record
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(WalletTransactionType.DEBIT);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(description);
        walletTransactionRepository.save(transaction);
        
        log.info("Debited {} {} from wallet userId={}, new balance={}", 
            amount, wallet.getCurrency(), userId, newBalance);
        
        return wallet;
    }
    
    /**
     * Credit driver wallet after ride completion
     * Credits the fare amount (after platform fee deduction) to driver's wallet
     * @param payment Payment entity
     */
    public void creditDriverWalletAfterRideCompletion(Payment payment) {
        if (payment.getStatus() != com.ridesharing.paymentservice.entity.PaymentStatus.SUCCESS) {
            throw new BadRequestException("Cannot credit wallet for non-successful payment");
        }
        
        // Credit fare amount to driver (platform fee already deducted)
        Double driverEarnings = payment.getFare();
        String description = String.format("Earnings from booking #%d", payment.getBookingId());
        
        creditWallet(payment.getDriverId(), driverEarnings, description, payment.getId());
        
        log.info("Credited driver wallet: driverId={}, amount={} {}, paymentId={}", 
            payment.getDriverId(), driverEarnings, payment.getCurrency(), payment.getId());
    }
    
    /**
     * Get wallet transactions
     * @param userId User ID
     * @return List of wallet transactions (empty list if wallet doesn't exist)
     */
    @Transactional(readOnly = true)
    public List<WalletTransaction> getWalletTransactions(Long userId) {
        Wallet wallet = getWallet(userId);
        if (wallet == null) {
            return List.of(); // Return empty list if wallet doesn't exist
        }
        return walletTransactionRepository.findByWalletOrderByCreatedAtDesc(wallet);
    }
}
