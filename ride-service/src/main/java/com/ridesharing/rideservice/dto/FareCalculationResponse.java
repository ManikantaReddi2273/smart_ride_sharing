package com.ridesharing.rideservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fare Calculation Response DTO
 * <p>
 * Represents the result of a fare calculation, including distance and
 * pricing information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FareCalculationResponse {

    /**
     * Distance for this route in kilometers.
     */
    private Double distanceKm;

    /**
     * Base fare amount applied to this calculation.
     */
    private Double baseFare;

    /**
     * Rate per kilometer applied to this calculation.
     */
    private Double ratePerKm;

    /**
     * Total fare for this route (base fare + distance component).
     */
    private Double totalFare;

    /**
     * Currency code (e.g., INR, USD).
     */
    private String currency;

    /**
     * Estimated travel time in seconds (optional).
     */
    private Long estimatedDurationSeconds;

    /**
     * Human-readable estimated travel time (e.g., "3 hours 30 mins").
     */
    private String estimatedDurationText;
}


