package com.ridesharing.userservice.repository;

import com.ridesharing.userservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Role Repository
 * Data access layer for Role entity
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    /**
     * Find role by name
     * @param name Role name (e.g., "DRIVER", "PASSENGER", "ADMIN")
     * @return Optional Role if found
     */
    Optional<Role> findByName(String name);
    
    /**
     * Check if role exists by name
     * @param name Role name
     * @return true if role exists, false otherwise
     */
    boolean existsByName(String name);
}

