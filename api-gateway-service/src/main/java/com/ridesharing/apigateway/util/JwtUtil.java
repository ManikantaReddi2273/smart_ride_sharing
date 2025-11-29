package com.ridesharing.apigateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * JWT Utility Class
 * Handles JWT token validation and extraction
 * Uses the same secret key as User Service for token validation
 */
@Component
public class JwtUtil {
    
    /**
     * JWT secret key for validating tokens
     * Must match the secret key used in User Service
     */
    @Value("${jwt.secret:SmartRideSharingSystemSecretKeyForJWTTokenGeneration2024}")
    private String secret;
    
    /**
     * Get signing key from secret
     * @return SecretKey for JWT validation
     */
    private SecretKey getSigningKey() {
        // Ensure secret is at least 32 bytes (256 bits) for HS256 algorithm
        String key = secret;
        if (key.length() < 32) {
            // Pad with zeros if secret is too short
            key = key + "0".repeat(32 - key.length());
        }
        // Use exactly 32 bytes for the key (first 32 characters)
        byte[] keyBytes = key.substring(0, 32).getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Extract all claims from token
     * @param token JWT token
     * @return Claims object
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Extract specific claim from token
     * @param token JWT token
     * @param claimsResolver Function to extract claim
     * @return Claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Extract user ID from token
     * @param token JWT token
     * @return User ID
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }
    
    /**
     * Extract email from token
     * @param token JWT token
     * @return Email address
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extract role from token
     * @param token JWT token
     * @return User role
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }
    
    /**
     * Extract expiration date from token
     * @param token JWT token
     * @return Expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * Check if token is expired
     * @param token JWT token
     * @return true if expired, false otherwise
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Validate token
     * @param token JWT token
     * @return true if token is valid, false otherwise
     */
    public Boolean validateToken(String token) {
        try {
            // Check if token is expired
            if (isTokenExpired(token)) {
                return false;
            }
            // If we can extract claims without exception, token is valid
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

