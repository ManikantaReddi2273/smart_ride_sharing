package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User Repository
 * Data access layer for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by email
     * @param email User's email address
     * @return Optional User if found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by phone number
     * @param phone User's phone number
     * @return Optional User if found
     */
    Optional<User> findByPhone(String phone);
    
    /**
     * Find user by email or phone
     * @param email User's email address
     * @param phone User's phone number
     * @return Optional User if found
     */
    @Query("SELECT u FROM User u WHERE u.email = :email OR u.phone = :phone")
    Optional<User> findByEmailOrPhone(@Param("email") String email, @Param("phone") String phone);
    
    /**
     * Check if user exists by email
     * @param email User's email address
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if user exists by phone
     * @param phone User's phone number
     * @return true if user exists, false otherwise
     */
    boolean existsByPhone(String phone);
    
    /**
     * Check if user exists by email or phone
     * @param email User's email address
     * @param phone User's phone number
     * @return true if user exists, false otherwise
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email OR u.phone = :phone")
    boolean existsByEmailOrPhone(@Param("email") String email, @Param("phone") String phone);
}

