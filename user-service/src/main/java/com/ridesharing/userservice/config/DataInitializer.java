package com.ridesharing.userservice.config;

import com.ridesharing.userservice.entity.Role;
import com.ridesharing.userservice.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Data Initializer
 * Initializes default roles in the database on application startup
 */
@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private RoleRepository roleRepository;
    
    /**
     * Initialize default roles if they don't exist
     */
    @Override
    public void run(String... args) throws Exception {
        // Create DRIVER role if it doesn't exist
        if (!roleRepository.existsByName("DRIVER")) {
            Role driverRole = new Role();
            driverRole.setName("DRIVER");
            driverRole.setDescription("Driver role - can post rides and manage vehicles");
            roleRepository.save(driverRole);
            System.out.println("Created DRIVER role");
        }
        
        // Create PASSENGER role if it doesn't exist
        if (!roleRepository.existsByName("PASSENGER")) {
            Role passengerRole = new Role();
            passengerRole.setName("PASSENGER");
            passengerRole.setDescription("Passenger role - can book rides");
            roleRepository.save(passengerRole);
            System.out.println("Created PASSENGER role");
        }
        
        // Create ADMIN role if it doesn't exist
        if (!roleRepository.existsByName("ADMIN")) {
            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("Admin role - can manage system and users");
            roleRepository.save(adminRole);
            System.out.println("Created ADMIN role");
        }
    }
}

