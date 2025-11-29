package com.ridesharing.userservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Utility Class
 * Handles JWT token generation, validation, and extraction
 */
@Component
public class JwtUtil {
    
    /**
     * JWT secret key for signing tokens
     * Should be at least 256 bits (32 characters) for HS256 algorithm
     */
    @Value("${jwt.secret:SmartRideSharingSystemSecretKeyForJWTTokenGeneration2024}")
    private String secret;
    
    /**
     * JWT token expiration time in milliseconds
     * Default: 24 hours (86400000 ms)
     */
    @Value("${jwt.expiration:86400000}")
    private Long expiration;
    
    /**
     * Get signing key from secret
     * @return SecretKey for JWT signing
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
     * Generate JWT token for user
     * @param userId User ID
     * @param email User's email
     * @param role User's role
     * @return JWT token string
     */
    public String generateToken(Long userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("role", role);
        return createToken(claims, email);
    }
    
    /**
     * Create JWT token with claims
     * @param claims Token claims (user information)
     * @param subject Token subject (usually email)
     * @return JWT token string
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
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
        return extractExpiration(token).before(new Date());
    }
    
    /**
     * Validate token
     * @param token JWT token
     * @param email User's email to validate against
     * @return true if token is valid, false otherwise
     */
    public Boolean validateToken(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return (tokenEmail.equals(email) && !isTokenExpired(token));
    }
    
    /**
     * Get token expiration time in milliseconds
     * @return Expiration time
     */
    public Long getExpirationTime() {
        return expiration;
    }
}

