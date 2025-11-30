package com.ridesharing.rideservice.controller;

import com.ridesharing.rideservice.dto.*;
import com.ridesharing.rideservice.entity.RideStatus;
import com.ridesharing.rideservice.exception.BadRequestException;
import com.ridesharing.rideservice.service.RideService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Ride Controller
 * Handles ride posting, searching, booking, and management endpoints
 * CORS is handled by API Gateway - no need for @CrossOrigin annotation
 */
@RestController
@RequestMapping("/api/rides")
@Slf4j
public class RideController {
    
    @Autowired
    private RideService rideService;
    
    @Autowired
    private com.ridesharing.rideservice.service.FareCalculationService fareCalculationService;
    
    @Autowired
    private com.ridesharing.rideservice.service.GoogleMapsService googleMapsService;
    
    /**
     * Post a new ride
     * POST /api/rides
     * Requires authentication. User must have at least one vehicle registered.
     * 
     * @param driverId User ID from gateway header (X-User-Id)
     * @param authorization Authorization header for User Service calls
     * @param request Ride request
     * @return RideResponse with ride details
     */
    @PostMapping
    public ResponseEntity<RideResponse> postRide(
            @RequestHeader("X-User-Id") Long driverId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RideRequest request) {
        RideResponse response = rideService.postRide(driverId, request, authorization);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Search rides with filters and intelligent route matching
     * GET /api/rides/search
     * Public endpoint (no authentication required, but optional for enhanced details)
     * 
     * @param source Source location (required)
     * @param destination Destination location (required)
     * @param rideDate Ride date (required)
     * @param sourceLatitude Source latitude (optional - enables intelligent route matching)
     * @param sourceLongitude Source longitude (optional - enables intelligent route matching)
     * @param destinationLatitude Destination latitude (optional - enables intelligent route matching)
     * @param destinationLongitude Destination longitude (optional - enables intelligent route matching)
     * @param minPrice Minimum price filter (optional)
     * @param maxPrice Maximum price filter (optional)
     * @param vehicleType Vehicle type filter (optional)
     * @param minRating Minimum driver rating filter (optional)
     * @param authorization Optional JWT token for fetching driver/vehicle details
     * @return List of matching rides
     */
    @GetMapping("/search")
    public ResponseEntity<List<RideResponse>> searchRides(
            @RequestParam(required = true) String source,
            @RequestParam(required = true) String destination,
            @RequestParam(required = true) String rideDate,
            @RequestParam(required = false) Double sourceLatitude,
            @RequestParam(required = false) Double sourceLongitude,
            @RequestParam(required = false) Double destinationLatitude,
            @RequestParam(required = false) Double destinationLongitude,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) Integer minRating,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        if (source == null || source.trim().isEmpty()) {
            throw new BadRequestException("Source location is required");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new BadRequestException("Destination location is required");
        }
        if (rideDate == null || rideDate.trim().isEmpty()) {
            throw new BadRequestException("Ride date is required");
        }
        
        RideSearchRequest request = new RideSearchRequest();
        request.setSource(source.trim());
        request.setDestination(destination.trim());
        try {
            request.setRideDate(java.time.LocalDate.parse(rideDate));
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format. Please use YYYY-MM-DD format");
        }
        // CRITICAL: Add coordinates if provided (from frontend autocomplete)
        // This enables intelligent route matching (passenger route anywhere along driver's journey)
        if (sourceLatitude != null && sourceLongitude != null) {
            request.setSourceLatitude(sourceLatitude);
            request.setSourceLongitude(sourceLongitude);
            log.info("✅ Received source coordinates: [lat={}, lon={}]", sourceLatitude, sourceLongitude);
        }
        if (destinationLatitude != null && destinationLongitude != null) {
            request.setDestinationLatitude(destinationLatitude);
            request.setDestinationLongitude(destinationLongitude);
            log.info("✅ Received destination coordinates: [lat={}, lon={}]", destinationLatitude, destinationLongitude);
        }
        
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setVehicleType(vehicleType);
        request.setMinRating(minRating);
        
        log.info("🔍 Search request - Source: '{}', Destination: '{}', Date: {}, Has coordinates: {}",
            source, destination, rideDate, 
            (sourceLatitude != null && destinationLatitude != null));
        
        List<RideResponse> rides = rideService.searchRides(request, authorization);
        return new ResponseEntity<>(rides, HttpStatus.OK);
    }
    
    /**
     * Get ride details by ID
     * GET /api/rides/{rideId}
     * Public endpoint (optional authentication for enhanced details)
     * 
     * @param rideId Ride ID
     * @param authorization Optional JWT token for fetching driver/vehicle details
     * @return RideResponse with ride details
     */
    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRideById(
            @PathVariable Long rideId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        RideResponse response = rideService.getRideById(rideId, authorization);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Book a seat on a ride
     * POST /api/rides/{rideId}/book
     * Requires authentication. Users cannot book their own rides.
     * 
     * @param rideId Ride ID
     * @param passengerId User ID from gateway header (X-User-Id)
     * @param authorization Authorization header for User Service calls
     * @param request Booking request
     * @return BookingResponse with booking details
     */
    @PostMapping("/{rideId}/book")
    public ResponseEntity<BookingResponse> bookSeat(
            @PathVariable Long rideId,
            @RequestHeader("X-User-Id") Long passengerId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody BookingRequest request) {
        BookingResponse response = rideService.bookSeat(rideId, passengerId, request, authorization);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Verify payment and confirm booking
     * POST /api/rides/bookings/{bookingId}/verify-payment
     * Requires authentication. Called by frontend after payment completion.
     * 
     * @param bookingId Booking ID
     * @param paymentVerificationRequest Payment verification request (from Razorpay)
     * @return Updated BookingResponse with confirmed booking
     */
    @PostMapping("/bookings/{bookingId}/verify-payment")
    public ResponseEntity<BookingResponse> verifyPaymentAndConfirmBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, Object> paymentVerificationRequest) {
        BookingResponse response = rideService.verifyPaymentAndConfirmBooking(bookingId, paymentVerificationRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Update ride details
     * PUT /api/rides/{rideId}
     * Requires authentication. Only the ride owner can update their ride.
     * 
     * @param rideId Ride ID
     * @param driverId User ID from gateway header (X-User-Id)
     * @param authorization Authorization header
     * @param request Updated ride request
     * @return Updated RideResponse
     */
    @PutMapping("/{rideId}")
    public ResponseEntity<RideResponse> updateRide(
            @PathVariable Long rideId,
            @RequestHeader("X-User-Id") Long driverId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RideRequest request) {
        RideResponse response = rideService.updateRide(rideId, driverId, request, authorization);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Cancel a ride
     * DELETE /api/rides/{rideId}
     * Requires authentication. Only the ride owner can cancel their ride.
     * 
     * @param rideId Ride ID
     * @param driverId User ID from gateway header (X-User-Id)
     * @return Cancelled RideResponse
     */
    @DeleteMapping("/{rideId}")
    public ResponseEntity<RideResponse> cancelRide(
            @PathVariable Long rideId,
            @RequestHeader("X-User-Id") Long driverId) {
        RideResponse response = rideService.cancelRide(rideId, driverId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Get all rides posted by current user
     * GET /api/rides/my-rides
     * Requires authentication. Returns all rides posted by the authenticated user.
     * 
     * @param driverId User ID from gateway header (X-User-Id)
     * @param authorization Authorization header for backfilling denormalized data
     * @return List of rides posted by the user
     */
    @GetMapping("/my-rides")
    public ResponseEntity<List<RideResponse>> getMyRides(
            @RequestHeader("X-User-Id") Long driverId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        List<RideResponse> rides = rideService.getMyRides(driverId, authorization);
        return new ResponseEntity<>(rides, HttpStatus.OK);
    }
    
    /**
     * Get all bookings made by current user
     * GET /api/rides/my-bookings
     * Requires authentication. Returns all bookings made by the authenticated user.
     * 
     * @param passengerId User ID from gateway header (X-User-Id)
     * @return List of bookings
     */
    @GetMapping("/my-bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @RequestHeader("X-User-Id") Long passengerId) {
        List<BookingResponse> bookings = rideService.getMyBookings(passengerId);
        return new ResponseEntity<>(bookings, HttpStatus.OK);
    }

    /**
     * Calculate fare for a route without creating a ride.
     * GET /api/rides/calculate-fare?source=...&destination=...
     * Optional coordinates: sourceLat, sourceLon, destLat, destLon
     * Public endpoint.
     *
     * @param source Source location name
     * @param destination Destination location name
     * @param sourceLat Source latitude (optional - if provided, skips geocoding)
     * @param sourceLon Source longitude (optional - if provided, skips geocoding)
     * @param destLat Destination latitude (optional - if provided, skips geocoding)
     * @param destLon Destination longitude (optional - if provided, skips geocoding)
     * @return FareCalculationResponse with distance and fare details
     */
    @GetMapping("/calculate-fare")
    public ResponseEntity<FareCalculationResponse> calculateFare(
            @RequestParam(name = "source") String source,
            @RequestParam(name = "destination") String destination,
            @RequestParam(name = "sourceLat", required = false) Double sourceLat,
            @RequestParam(name = "sourceLon", required = false) Double sourceLon,
            @RequestParam(name = "destLat", required = false) Double destLat,
            @RequestParam(name = "destLon", required = false) Double destLon) {
        if (source == null || source.trim().isEmpty()) {
            throw new BadRequestException("Source location is required");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new BadRequestException("Destination location is required");
        }
        try {
            FareCalculationResponse response;
            
            // CRITICAL: Use coordinates if provided (from frontend autocomplete) - 100% accurate
            if (sourceLat != null && sourceLon != null && destLat != null && destLon != null) {
                response = fareCalculationService.calculateFareFromCoordinates(
                        sourceLat, sourceLon, destLat, destLon,
                        source.trim(), destination.trim()
                );
            } else {
                // Fallback to geocoding
                response = fareCalculationService.calculateFare(
                        source.trim(),
                        destination.trim()
                );
            }
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (RuntimeException ex) {
            // Re-throw as BadRequestException to ensure proper error response
            throw new BadRequestException(ex.getMessage());
        }
    }
    
    /**
     * Get address autocomplete suggestions
     * GET /api/rides/address-suggestions?query=...
     * Public endpoint for address autocomplete functionality.
     *
     * @param query Search query (minimum 2 characters)
     * @return List of address suggestions
     */
    @GetMapping("/address-suggestions")
    public ResponseEntity<List<Map<String, Object>>> getAddressSuggestions(
            @RequestParam(name = "query") String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ResponseEntity<>(java.util.Collections.emptyList(), HttpStatus.OK);
        }
        try {
            List<Map<String, Object>> suggestions = googleMapsService.getAddressSuggestions(query.trim());
            return new ResponseEntity<>(suggestions, HttpStatus.OK);
        } catch (Exception ex) {
            // Return empty list on error instead of throwing exception
            // This ensures autocomplete doesn't break the UI
            return new ResponseEntity<>(java.util.Collections.emptyList(), HttpStatus.OK);
        }
    }
    
    /**
     * Update ride status
     * PUT /api/rides/{rideId}/status
     * Requires authentication. Only the ride owner can update ride status.
     * 
     * @param rideId Ride ID
     * @param driverId User ID from gateway header (X-User-Id)
     * @param status New status
     * @return Updated RideResponse
     */
    @PutMapping("/{rideId}/status")
    public ResponseEntity<RideResponse> updateRideStatus(
            @PathVariable Long rideId,
            @RequestHeader("X-User-Id") Long driverId,
            @RequestParam String status) {
        RideStatus rideStatus = RideStatus.valueOf(status.toUpperCase());
        RideResponse response = rideService.updateRideStatus(rideId, driverId, rideStatus);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

