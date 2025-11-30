package com.ridesharing.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Payment Service Application
 * 
 * This is the Payment Processing and Wallet Management Service for the Smart Ride Sharing System.
 * It handles:
 * - Payment gateway integration (Razorpay)
 * - Payment processing and verification
 * - Wallet management for drivers
 * - Transaction history
 * - Refund processing
 * 
 * Port: 8084
 * Database: payment_db
 * 
 * @author Smart Ride Sharing System
 * @version 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@EnableAsync
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
