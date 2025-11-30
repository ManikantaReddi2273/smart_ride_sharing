package com.ridesharing.paymentservice.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.ridesharing.paymentservice.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Razorpay Service
 * Handles all Razorpay API interactions
 */
@Service
@Slf4j
public class RazorpayService {
    
    private final RazorpayClient razorpayClient;
    private final String razorpayKeySecret;
    
    @Autowired
    public RazorpayService(RazorpayClient razorpayClient, 
                           @Value("${razorpay.key.secret}") String razorpayKeySecret) {
        this.razorpayClient = razorpayClient;
        this.razorpayKeySecret = razorpayKeySecret;
    }
    
    /**
     * Create a Razorpay order
     * @param amount Amount in paise (e.g., 10000 = ₹100.00)
     * @param currency Currency code (default: INR)
     * @param receipt Receipt ID (optional)
     * @return Razorpay Order object
     */
    public Order createOrder(Long amount, String currency, String receipt) {
        try {
            // CRITICAL: Razorpay SDK requires exact types - String values must be proper String objects
            // The ClassCastException occurs when JSONObject stores values that Razorpay SDK can't handle
            
            // Validate and convert amount to primitive long
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than 0");
            }
            long amountValue = amount.longValue();
            
            // Validate and normalize currency - ensure it's a proper String object
            String currencyCode = (currency != null && !currency.trim().isEmpty()) 
                ? currency.trim() 
                : "INR";
            
            // Validate and normalize receipt - ensure it's a proper String object
            String receiptValue;
            if (receipt != null && !receipt.trim().isEmpty()) {
                receiptValue = receipt.trim();
            } else {
                // Generate unique receipt
                receiptValue = "booking_" + System.currentTimeMillis();
            }
            
            // CRITICAL: Build JSONObject using proper JSON escaping to avoid char[] casting issues
            // The Razorpay SDK has strict type requirements - building from JSON string
            // ensures all values are properly serialized as JSON types, not Java char arrays
            // Use JSONObject.quote() for proper JSON string escaping
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"amount\":").append(amountValue);
            jsonBuilder.append(",\"currency\":").append(JSONObject.quote(currencyCode));
            jsonBuilder.append(",\"receipt\":").append(JSONObject.quote(receiptValue));
            jsonBuilder.append(",\"payment_capture\":1");
            jsonBuilder.append("}");
            
            String jsonString = jsonBuilder.toString();
            
            // Parse JSON string into JSONObject - this ensures proper type conversion
            // All values are now proper JSON types, not Java char arrays
            JSONObject orderRequest = new JSONObject(jsonString);
            
            log.info("Creating Razorpay order: amount={} paise, currency={}, receipt={}", 
                amountValue, currencyCode, receiptValue);
            log.debug("Order request JSON: {}", orderRequest.toString());
            
            // CRITICAL: Create order - this is where the ClassCastException was occurring
            Order order = razorpayClient.orders.create(orderRequest);
            
            // CRITICAL: Safely extract order ID - handle both String and char[] cases
            // The Razorpay SDK might return order ID as char[] instead of String
            Object orderIdObj = order.get("id");
            String orderId;
            if (orderIdObj != null) {
                if (orderIdObj instanceof String) {
                    orderId = (String) orderIdObj;
                } else if (orderIdObj instanceof char[]) {
                    orderId = new String((char[]) orderIdObj);
                } else {
                    // Fallback: convert to string safely
                    orderId = String.valueOf(orderIdObj);
                }
            } else {
                orderId = "unknown";
                log.warn("⚠️ Razorpay order ID is null!");
            }
            log.info("✅ Razorpay order created successfully: orderId={}", orderId);
            
            return order;
        } catch (RazorpayException e) {
            log.error("❌ RazorpayException creating order: {}", e.getMessage(), e);
            log.error("❌ Exception class: {}, cause: {}", e.getClass().getName(), 
                e.getCause() != null ? e.getCause().getClass().getName() : "none");
            throw new BadRequestException("Failed to create payment order: " + e.getMessage());
        } catch (ClassCastException e) {
            log.error("❌ ClassCastException creating Razorpay order: {}", e.getMessage(), e);
            log.error("❌ This indicates a type mismatch - String vs char[] issue");
            log.error("❌ Stack trace: ", e);
            throw new BadRequestException("Failed to create payment order: Type conversion error. " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("❌ IllegalArgumentException: {}", e.getMessage());
            throw new BadRequestException("Invalid payment order parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error creating Razorpay order: {}", e.getMessage(), e);
            log.error("❌ Exception class: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("❌ Root cause: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw new BadRequestException("Failed to create payment order: " + e.getMessage());
        }
    }
    
    /**
     * Verify Razorpay payment signature
     * @param orderId Razorpay order ID
     * @param paymentId Razorpay payment ID
     * @param signature Razorpay signature
     * @return true if signature is valid
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            String generatedSignature = calculateHMAC(payload, razorpayKeySecret);
            
            boolean isValid = generatedSignature.equals(signature);
            log.info("Payment signature verification: orderId={}, paymentId={}, isValid={}", 
                orderId, paymentId, String.valueOf(isValid));
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying payment signature: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Calculate HMAC SHA256 signature
     * @param payload Payload to sign
     * @param secret Secret key
     * @return HMAC signature
     */
    private String calculateHMAC(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error calculating HMAC: {}", e.getMessage(), e);
            throw new RuntimeException("Error calculating HMAC", e);
        }
    }
}
