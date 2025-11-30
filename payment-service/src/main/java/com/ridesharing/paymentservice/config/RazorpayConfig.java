package com.ridesharing.paymentservice.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Razorpay Configuration
 * Configures Razorpay client bean
 */
@Configuration
@Slf4j
public class RazorpayConfig {
    
    @Value("${razorpay.key.id}")
    private String razorpayKeyId;
    
    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;
    
    @Bean
    public RazorpayClient razorpayClient() {
        try {
            log.info("Initializing Razorpay client with key ID: {}", 
                razorpayKeyId != null && razorpayKeyId.length() > 8 
                    ? razorpayKeyId.substring(0, 8) + "..." 
                    : "***");
            // RazorpayClient constructor: (keyId, keySecret)
            return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Razorpay client", e);
        }
    }
    
    @Bean
    public String razorpayKeyId() {
        return razorpayKeyId;
    }
}
