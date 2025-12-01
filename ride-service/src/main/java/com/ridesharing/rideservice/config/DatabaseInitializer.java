package com.ridesharing.rideservice.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database Initializer
 * Ensures the reviews table exists on startup
 */
@Component
@Slf4j
public class DatabaseInitializer {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initializeDatabase() {
        if (jdbcTemplate == null) {
            log.warn("JdbcTemplate not available, skipping database initialization");
            return;
        }

        try {
            // Check if reviews table exists
            String checkTableSql = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = 'reviews'";
            
            Integer tableCount = jdbcTemplate.queryForObject(checkTableSql, Integer.class);
            
            if (tableCount == null || tableCount == 0) {
                log.warn("⚠️ Reviews table does not exist. Creating it now...");
                createReviewsTable();
            } else {
                log.info("✅ Reviews table exists in database");
            }
        } catch (Exception e) {
            log.error("Error checking/creating reviews table: {}", e.getMessage(), e);
            // Try to create table anyway
            try {
                createReviewsTable();
            } catch (Exception createEx) {
                log.error("Failed to create reviews table: {}", createEx.getMessage());
            }
        }
    }

    private void createReviewsTable() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS reviews (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    booking_id BIGINT NOT NULL,
                    reviewer_id BIGINT NOT NULL,
                    reviewee_id BIGINT NOT NULL,
                    rating INT NOT NULL,
                    comment TEXT,
                    review_type VARCHAR(30) NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME,
                    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
                    INDEX idx_booking_id (booking_id),
                    INDEX idx_reviewer_id (reviewer_id),
                    INDEX idx_reviewee_id (reviewee_id),
                    UNIQUE INDEX idx_booking_reviewer (booking_id, reviewer_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            jdbcTemplate.execute(createTableSql);
            log.info("✅ Successfully created reviews table");
        } catch (Exception e) {
            log.error("Failed to create reviews table: {}", e.getMessage(), e);
            throw e;
        }
    }
}
