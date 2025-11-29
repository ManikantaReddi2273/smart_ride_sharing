package com.ridesharing.rideservice.service;

import com.ridesharing.rideservice.dto.FareCalculationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fare Calculation Service
 * <p>
 * Encapsulates business logic for calculating dynamic fares based on
 * distance returned by Google Maps Distance Matrix API.
 */
@Service
@Slf4j
public class FareCalculationService {

    @Value("${fare.base-fare:50.0}")
    private Double baseFare;

    @Value("${fare.rate-per-km:10.0}")
    private Double ratePerKm;

    @Value("${fare.currency:INR}")
    private String currency;

    private final GoogleMapsService googleMapsService;

    public FareCalculationService(GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

    /**
     * Calculate fare using coordinates directly (FASTEST & MOST ACCURATE)
     * This method skips geocoding and uses exact coordinates from frontend
     *
     * @param sourceLat      Source latitude
     * @param sourceLon      Source longitude
     * @param destLat        Destination latitude
     * @param destLon        Destination longitude
     * @param sourceName     Source location name (for logging)
     * @param destName       Destination location name (for logging)
     * @return FareCalculationResponse with distance and fare details
     */
    public FareCalculationResponse calculateFareFromCoordinates(
            Double sourceLat, Double sourceLon, Double destLat, Double destLon,
            String sourceName, String destName) {
        
        GoogleMapsService.DistanceMatrixResult distanceResult =
                googleMapsService.calculateDistanceFromCoordinates(
                    sourceLat, sourceLon, destLat, destLon, sourceName, destName);

        double distanceKm = roundToTwoDecimals(distanceResult.getDistanceKm());
        double totalFare = roundToTwoDecimals(baseFare + (ratePerKm * distanceKm));

        FareCalculationResponse response = new FareCalculationResponse();
        response.setDistanceKm(distanceKm);
        response.setBaseFare(baseFare);
        response.setRatePerKm(ratePerKm);
        response.setTotalFare(totalFare);
        response.setCurrency(currency);
        response.setEstimatedDurationSeconds(distanceResult.getDurationSeconds());
        response.setEstimatedDurationText(distanceResult.getDurationText());

        log.info("✅ Calculated fare using coordinates - route '{}' -> '{}': distance={} km, fare={} {}",
                sourceName, destName, distanceKm, totalFare, currency);

        return response;
    }
    
    /**
     * Calculate fare for a full ride from source to destination.
     * This method geocodes addresses first, then calculates distance.
     * Use calculateFareFromCoordinates() if you already have coordinates (faster & more accurate).
     *
     * @param source      Source address
     * @param destination Destination address
     * @return FareCalculationResponse with distance and fare details
     */
    public FareCalculationResponse calculateFare(String source, String destination) {
        GoogleMapsService.DistanceMatrixResult distanceResult =
                googleMapsService.calculateDistance(source, destination);

        double distanceKm = roundToTwoDecimals(distanceResult.getDistanceKm());
        double totalFare = roundToTwoDecimals(baseFare + (ratePerKm * distanceKm));

        FareCalculationResponse response = new FareCalculationResponse();
        response.setDistanceKm(distanceKm);
        response.setBaseFare(baseFare);
        response.setRatePerKm(ratePerKm);
        response.setTotalFare(totalFare);
        response.setCurrency(currency);
        response.setEstimatedDurationSeconds(distanceResult.getDurationSeconds());
        response.setEstimatedDurationText(distanceResult.getDurationText());

        log.info("Calculated fare for route '{}' -> '{}': distance={} km, fare={} {}",
                source, destination, distanceKm, totalFare, currency);

        return response;
    }

    /**
     * Calculate fare for a passenger who may join or exit in the middle of
     * the driver's route.
     *
     * @param rideSource           Full ride source
     * @param rideDestination      Full ride destination
     * @param passengerSource      Optional passenger source (null = rideSource)
     * @param passengerDestination Optional passenger destination (null = rideDestination)
     * @return FareCalculationResponse for the passenger segment
     */
    public FareCalculationResponse calculatePassengerFare(
            String rideSource,
            String rideDestination,
            String passengerSource,
            String passengerDestination) {

        String actualSource = (passengerSource != null && !passengerSource.trim().isEmpty())
                ? passengerSource.trim()
                : rideSource;

        String actualDestination = (passengerDestination != null && !passengerDestination.trim().isEmpty())
                ? passengerDestination.trim()
                : rideDestination;

        log.info("Calculating passenger fare for segment '{}' -> '{}'", actualSource, actualDestination);

        // Delegate to main fare calculation
        return calculateFare(actualSource, actualDestination);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}


