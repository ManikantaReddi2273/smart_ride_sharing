
package com.ridesharing.rideservice.service;
import com.ridesharing.rideservice.dto.*;
import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.BookingStatus;
import com.ridesharing.rideservice.entity.Ride;
import com.ridesharing.rideservice.entity.RideStatus;
import com.ridesharing.rideservice.exception.BadRequestException;
import com.ridesharing.rideservice.exception.ResourceNotFoundException;
import com.ridesharing.rideservice.feign.PaymentServiceClient;
import com.ridesharing.rideservice.feign.UserServiceClient;
import com.ridesharing.rideservice.repository.BookingRepository;
import com.ridesharing.rideservice.repository.RideRepository;
import com.ridesharing.rideservice.util.RouteGeometryUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ride Service
 * Handles ride posting, searching, booking, and management business logic
 */
@Service
@Slf4j
@Transactional
public class RideService {
    
    @Autowired
    private RideRepository rideRepository;
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Autowired
    private UserServiceClient userServiceClient;
    
    @Autowired
    private PaymentServiceClient paymentServiceClient;

    @Autowired
    private FareCalculationService fareCalculationService;
    
    @Autowired
    private GoogleMapsService googleMapsService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private OtpService otpService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RouteGeometryUtil routeGeometryUtil;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private ReviewService reviewService;
    
    @Value("${fare.base-fare:50.0}")
    private Double baseFare;
    
    @Value("${fare.rate-per-km:10.0}")
    private Double ratePerKm;
    
    @Value("${fare.currency:INR}")
    private String currency;
    
    @Value("${route.matching.synthetic-waypoints:5}")
    private int syntheticWaypoints;
    
    @Value("${route.matching.max-distance-km:30.0}")
    private double maxDistanceFromRouteKm;
    
    /**
     * Helper to check if coordinates look valid (India range or non-zero check).
     * Used to detect invalid coordinates like (0,0) from frontend default state.
     * 
     * @param lat Latitude
     * @param lon Longitude
     * @return true if coordinates are valid (not null, not 0,0, and in India range)
     */
    private boolean isValidIndiaCoord(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return false;
        }
        
        // CRITICAL: Check for (0,0) - common default in React state or bug in props
        // This would be in Gulf of Guinea near Africa, not India
        if (lat == 0.0 && lon == 0.0) {
            log.warn("⚠️ Detected invalid coordinates (0,0) - likely frontend default state");
            return false;
        }
        
        // India bounding box (roughly)
        // Longitude: 68°E to 97°E, Latitude: 6°N to 37°N
        boolean inIndiaRange = lon >= 68 && lon <= 97 && lat >= 6 && lat <= 37;
        
        if (!inIndiaRange) {
            log.debug("Coordinates [lat={}, lon={}] are outside India range", lat, lon);
        }
        
        return inIndiaRange;
    }
    
    /**
     * Post a new ride
     * @param driverId User's ID (must have at least one vehicle registered)
     * @param request Ride request
     * @param authorization JWT token for User Service calls
     * @return RideResponse with ride details
     */
    public RideResponse postRide(Long driverId, RideRequest request, String authorization) {
        // Validate vehicle belongs to driver
        // User Service extracts user ID from JWT token automatically
        List<Map<String, Object>> vehicles;
        try {
            vehicles = userServiceClient.getUserVehicles(authorization);
        } catch (Exception e) {
            throw new BadRequestException("Failed to fetch vehicles from User Service: " + e.getMessage());
        }
        
        // Check if vehicles list is empty
        if (vehicles == null || vehicles.isEmpty()) {
            throw new BadRequestException("Driver has no vehicles. Please add a vehicle first.");
        }
        
        // Find vehicle with proper type handling (Integer/Long)
        Map<String, Object> vehicle = vehicles.stream()
            .filter(v -> {
                Object idObj = v.get("id");
                if (idObj == null) return false;
                // Handle both Integer and Long types
                Long vehicleId = idObj instanceof Long ? (Long) idObj : 
                                idObj instanceof Integer ? ((Integer) idObj).longValue() : null;
                return vehicleId != null && vehicleId.equals(request.getVehicleId());
            })
            .findFirst()
            .orElseThrow(() -> new BadRequestException(
                String.format("Vehicle with ID %d not found or does not belong to driver. Available vehicles: %s", 
                    request.getVehicleId(), 
                    vehicles.stream()
                        .map(v -> v.get("id").toString())
                        .collect(java.util.stream.Collectors.joining(", "))
                )
            ));
        
        // Get driver profile
        // User Service extracts user ID from JWT token automatically
        Map<String, Object> driverProfile;
        try {
            driverProfile = userServiceClient.getUserProfile(authorization);
        } catch (Exception e) {
            throw new BadRequestException("Failed to fetch driver profile from User Service: " + e.getMessage());
        }
        
        // Calculate fare and distance for the full route
        // CRITICAL: Use coordinates if provided (from frontend autocomplete) - this is 100% accurate
        // Otherwise fallback to geocoding
        FareCalculationResponse fareResponse;
        GoogleMapsService.DistanceMatrixResult distanceResult = null;
        try {
            if (request.getSourceLatitude() != null && request.getSourceLongitude() != null &&
                request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
                // Use coordinates directly - FASTEST & MOST ACCURATE (no geocoding errors)
                log.info("✅ Using coordinates directly from frontend - skipping geocoding");
                
                // Get distance result with geometry (used for both fare and route geometry)
                // This single API call provides both distance/duration and route geometry
                distanceResult = googleMapsService.calculateDistanceFromCoordinates(
                    request.getSourceLatitude(),
                    request.getSourceLongitude(),
                    request.getDestinationLatitude(),
                    request.getDestinationLongitude(),
                    request.getSource(),
                    request.getDestination()
                );
                
                // Calculate fare from the distance result (reusing the API response)
                // This avoids duplicate API calls while maintaining fare calculation consistency
                double distanceKm = Math.round(distanceResult.getDistanceKm() * 100.0) / 100.0;
                double totalFare = Math.round((baseFare + (ratePerKm * distanceKm)) * 100.0) / 100.0;
                
                fareResponse = new FareCalculationResponse();
                fareResponse.setDistanceKm(distanceKm);
                fareResponse.setBaseFare(baseFare);
                fareResponse.setRatePerKm(ratePerKm);
                fareResponse.setTotalFare(totalFare);
                fareResponse.setCurrency(currency);
                fareResponse.setEstimatedDurationSeconds(distanceResult.getDurationSeconds());
                fareResponse.setEstimatedDurationText(distanceResult.getDurationText());
            } else {
                // Fallback to geocoding (if coordinates not provided)
                log.info("⚠️ Coordinates not provided - using geocoding (may have errors)");
                fareResponse = fareCalculationService.calculateFare(
                        request.getSource(),
                        request.getDestination()
                );
                
                // Get geometry separately for geocoded addresses
                try {
                    String[] sourceCoords = googleMapsService.geocodeAddress(request.getSource());
                    String[] destCoords = googleMapsService.geocodeAddress(request.getDestination());
                    // Use the public method that accepts coordinate arrays
                    // Convert String[] to Double for the public method
                    double sourceLat = Double.parseDouble(sourceCoords[1]);
                    double sourceLon = Double.parseDouble(sourceCoords[0]);
                    double destLat = Double.parseDouble(destCoords[1]);
                    double destLon = Double.parseDouble(destCoords[0]);
                    distanceResult = googleMapsService.calculateDistanceFromCoordinates(
                        sourceLat, sourceLon, destLat, destLon,
                        request.getSource(), request.getDestination()
                    );
                } catch (Exception ex) {
                    log.warn("Failed to fetch route geometry for geocoded addresses: {}", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            throw new BadRequestException("Failed to calculate fare for the given route: " + ex.getMessage());
        }

        // Create new ride
        Ride ride = new Ride();
        ride.setDriverId(driverId);
        ride.setVehicleId(request.getVehicleId());
        ride.setSource(request.getSource());
        ride.setDestination(request.getDestination());
        ride.setRideDate(request.getRideDate());
        ride.setRideTime(request.getRideTime());
        ride.setTotalSeats(request.getTotalSeats());
        ride.setAvailableSeats(request.getTotalSeats());
        ride.setStatus(RideStatus.POSTED);
        ride.setNotes(request.getNotes());

        // Set fare-related fields
        ride.setDistanceKm(fareResponse.getDistanceKm());
        ride.setTotalFare(fareResponse.getTotalFare());
        ride.setBaseFare(fareResponse.getBaseFare());
        ride.setRatePerKm(fareResponse.getRatePerKm());
        ride.setCurrency(fareResponse.getCurrency());
        
        // CRITICAL: Store route geometry for partial route matching
        // This is ESSENTIAL for partial route matching to work
        if (distanceResult != null && distanceResult.getRouteGeometry() != null) {
            ride.setRouteGeometry(distanceResult.getRouteGeometry());
            List<double[]> parsedGeometry = routeGeometryUtil.parseRouteGeometry(distanceResult.getRouteGeometry());
            int pointCount = parsedGeometry.size();
            if (pointCount > 0) {
                log.info("✅ Stored route geometry for ride ({} coordinate points)", pointCount);
                log.info("   Route starts: [lon={}, lat={}], ends: [lon={}, lat={}]", 
                    parsedGeometry.get(0)[0], parsedGeometry.get(0)[1],
                    parsedGeometry.get(pointCount - 1)[0], parsedGeometry.get(pointCount - 1)[1]);
            } else {
                log.error("❌ CRITICAL: Route geometry parsing resulted in 0 points! Geometry JSON length: {}", 
                    distanceResult.getRouteGeometry().length());
                // Try to generate synthetic polyline as fallback
                try {
                    if (request.getSourceLatitude() != null && request.getSourceLongitude() != null &&
                        request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
                        List<double[]> syntheticPolyline = routeGeometryUtil.generateSyntheticPolyline(
                            request.getSourceLatitude(), request.getSourceLongitude(),
                            request.getDestinationLatitude(), request.getDestinationLongitude(),
                            syntheticWaypoints
                        );
                        String syntheticGeometryJson = objectMapper.writeValueAsString(syntheticPolyline);
                        ride.setRouteGeometry(syntheticGeometryJson);
                        log.warn("⚠️ Generated synthetic polyline as fallback ({} points)", syntheticPolyline.size());
                    }
                } catch (Exception ex) {
                    log.error("❌ Failed to generate synthetic polyline fallback: {}", ex.getMessage());
                }
            }
        } else {
            log.error("❌ CRITICAL: Route geometry not available for ride - partial route matching will be LIMITED");
            log.error("   This ride will only match exact text matches or use synthetic polyline fallback");
            // Try to generate synthetic polyline if coordinates are available
            try {
                if (request.getSourceLatitude() != null && request.getSourceLongitude() != null &&
                    request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
                    List<double[]> syntheticPolyline = routeGeometryUtil.generateSyntheticPolyline(
                        request.getSourceLatitude(), request.getSourceLongitude(),
                        request.getDestinationLatitude(), request.getDestinationLongitude(),
                        syntheticWaypoints
                    );
                    String syntheticGeometryJson = objectMapper.writeValueAsString(syntheticPolyline);
                    ride.setRouteGeometry(syntheticGeometryJson);
                    log.warn("⚠️ Generated synthetic polyline as fallback ({} points)", syntheticPolyline.size());
                } else {
                    log.error("❌ Cannot generate synthetic polyline - coordinates not available");
                }
            } catch (Exception ex) {
                log.error("❌ Failed to generate synthetic polyline: {}", ex.getMessage());
            }
        }
        
        // Store driver and vehicle details (denormalized for search results)
        if (driverProfile != null) {
            ride.setDriverName((String) driverProfile.get("name"));
        }
        if (vehicle != null) {
            ride.setVehicleModel((String) vehicle.get("model"));
            ride.setVehicleLicensePlate((String) vehicle.get("licensePlate"));
            ride.setVehicleColor((String) vehicle.get("color"));
            ride.setVehicleCapacity((Integer) vehicle.get("capacity"));
        }
        
        ride = rideRepository.save(ride);
        
        return buildRideResponse(ride, driverProfile, vehicle);
    }
    
    /**
     * Search rides with filters and intelligent route matching
     * If coordinates are provided, finds rides where passenger's route lies anywhere along driver's journey
     * @param request Search request with filters and optional coordinates
     * @param authorization Optional JWT token for fetching driver/vehicle details
     * @return List of matching rides
     */
    @Transactional
    public List<RideResponse> searchRides(RideSearchRequest request, String authorization) {
        // Active statuses for search (only show available rides)
        List<RideStatus> activeStatuses = Arrays.asList(RideStatus.POSTED, RideStatus.BOOKED);
        
        List<Ride> rides;
        
        // Get all rides for the date first
        rides = rideRepository.findByRideDateAndStatusInAndAvailableSeatsGreaterThan(
            request.getRideDate(),
            activeStatuses,
            0
        );
        
        // CRITICAL: Filter out rides where scheduled time has passed - passengers can only see/book future rides
        LocalDateTime currentDateTime = LocalDateTime.now();
        rides = rides.stream()
            .filter(ride -> {
                LocalDateTime scheduledDateTime = LocalDateTime.of(ride.getRideDate(), ride.getRideTime());
                boolean isFuture = currentDateTime.isBefore(scheduledDateTime);
                if (!isFuture) {
                    log.debug("Filtered out expired ride ID {} - scheduled: {}, current: {}", 
                        ride.getId(), scheduledDateTime, currentDateTime);
                }
                return isFuture;
            })
            .collect(Collectors.toList());
        
        log.info("🔍 SEARCH RIDES - Found {} total rides for date {} (after filtering expired rides)", 
            rides.size(), request.getRideDate());
        log.info("   Search request - Source: '{}', Destination: '{}'", request.getSource(), request.getDestination());
        log.info("   Search request coordinates - Source: [lat={}, lon={}], Destination: [lat={}, lon={}]", 
            request.getSourceLatitude(), request.getSourceLongitude(), 
            request.getDestinationLatitude(), request.getDestinationLongitude());
        
        // CRITICAL: Log all rides found for debugging
        if (rides.isEmpty()) {
            log.warn("⚠️⚠️⚠️ NO RIDES FOUND for date {} - Check if any rides are posted for this date!", request.getRideDate());
        } else {
            log.info("📋 Rides found for date {}:", request.getRideDate());
            for (Ride ride : rides) {
                log.info("   Ride ID {}: {} -> {} (Status: {}, Available Seats: {}, Has Geometry: {})", 
                    ride.getId(), ride.getSource(), ride.getDestination(), 
                    ride.getStatus(), ride.getAvailableSeats(),
                    (ride.getRouteGeometry() != null && !ride.getRouteGeometry().trim().isEmpty()) ? "YES" : "NO");
            }
        }
        
        // CRITICAL: Geocode passenger source/destination if coordinates not provided OR invalid
        // This enables partial route matching even when user types manually (not using autocomplete)
        // FIX A: Also validate coordinates - frontend may send (0,0) from default React state
        Double passengerSourceLat = request.getSourceLatitude();
        Double passengerSourceLon = request.getSourceLongitude();
        Double passengerDestLat = request.getDestinationLatitude();
        Double passengerDestLon = request.getDestinationLongitude();
        
        // Validate source coordinates - geocode if null OR invalid (e.g., 0,0)
        if (!isValidIndiaCoord(passengerSourceLat, passengerSourceLon)) {
            try {
                log.info("🔍 Geocoding passenger source: '{}' (coordinates missing/invalid: lat={}, lon={})", 
                    request.getSource(), passengerSourceLat, passengerSourceLon);
                String[] sourceCoords = googleMapsService.geocodeAddress(request.getSource());
                if (sourceCoords != null && sourceCoords.length >= 2) {
                    // CRITICAL: geocodeAddress returns [lon, lat] format (confirmed in GoogleMapsService)
                    // OpenRouteService geocoding returns [lon, lat] as per GeoJSON standard
                    passengerSourceLon = Double.parseDouble(sourceCoords[0]);
                    passengerSourceLat = Double.parseDouble(sourceCoords[1]);
                    log.info("✅ Geocoded passenger source: [lat={}, lon={}]", passengerSourceLat, passengerSourceLon);
                    
                    // Validate geocoded result
                    if (!isValidIndiaCoord(passengerSourceLat, passengerSourceLon)) {
                        log.error("❌ Geocoded coordinates are still invalid: [lat={}, lon={}]. " +
                                "This will cause partial matching to fail.", passengerSourceLat, passengerSourceLon);
                        passengerSourceLat = null;
                        passengerSourceLon = null;
                    }
                } else {
                    log.warn("⚠️ Failed to geocode passenger source: '{}'", request.getSource());
                    passengerSourceLat = null;
                    passengerSourceLon = null;
                }
            } catch (Exception ex) {
                log.warn("⚠️ Error geocoding passenger source '{}': {}", request.getSource(), ex.getMessage());
                passengerSourceLat = null;
                passengerSourceLon = null;
            }
        } else {
            log.info("✅ Using provided passenger source coordinates: [lat={}, lon={}]", passengerSourceLat, passengerSourceLon);
        }
        
        // Validate destination coordinates - geocode if null OR invalid (e.g., 0,0)
        if (!isValidIndiaCoord(passengerDestLat, passengerDestLon)) {
            try {
                log.info("🔍 Geocoding passenger destination: '{}' (coordinates missing/invalid: lat={}, lon={})", 
                    request.getDestination(), passengerDestLat, passengerDestLon);
                String[] destCoords = googleMapsService.geocodeAddress(request.getDestination());
                if (destCoords != null && destCoords.length >= 2) {
                    // CRITICAL: geocodeAddress returns [lon, lat] format (confirmed in GoogleMapsService)
                    // OpenRouteService geocoding returns [lon, lat] as per GeoJSON standard
                    passengerDestLon = Double.parseDouble(destCoords[0]);
                    passengerDestLat = Double.parseDouble(destCoords[1]);
                    log.info("✅ Geocoded passenger destination: [lat={}, lon={}]", passengerDestLat, passengerDestLon);
                    
                    // Validate geocoded result
                    if (!isValidIndiaCoord(passengerDestLat, passengerDestLon)) {
                        log.error("❌ Geocoded coordinates are still invalid: [lat={}, lon={}]. " +
                                "This will cause partial matching to fail.", passengerDestLat, passengerDestLon);
                        passengerDestLat = null;
                        passengerDestLon = null;
                    }
                } else {
                    log.warn("⚠️ Failed to geocode passenger destination: '{}'", request.getDestination());
                    passengerDestLat = null;
                    passengerDestLon = null;
                }
            } catch (Exception ex) {
                log.warn("⚠️ Error geocoding passenger destination '{}': {}", request.getDestination(), ex.getMessage());
                passengerDestLat = null;
                passengerDestLon = null;
            }
        } else {
            log.info("✅ Using provided passenger destination coordinates: [lat={}, lon={}]", passengerDestLat, passengerDestLon);
        }
        
        // Prepare passenger coordinates for matching (if available)
        final Double finalSourceLat = passengerSourceLat;
        final Double finalSourceLon = passengerSourceLon;
        final Double finalDestLat = passengerDestLat;
        final Double finalDestLon = passengerDestLon;
        
        // Filter rides based on source and destination matching
        rides = rides.stream()
            .filter(ride -> {
                log.info("🔍🔍🔍 PROCESSING RIDE {}: {} -> {} (search: {} -> {})", 
                    ride.getId(), ride.getSource(), ride.getDestination(),
                    request.getSource(), request.getDestination());
                
                // ALWAYS include exact text matches (same source and destination)
                // Use case-insensitive partial matching for flexibility
                String searchSource = normalizeLocationName(request.getSource());
                String searchDest = normalizeLocationName(request.getDestination());
                String rideSource = normalizeLocationName(ride.getSource());
                String rideDest = normalizeLocationName(ride.getDestination());
                
                log.info("   Text matching - Search: '{}' -> '{}', Ride: '{}' -> '{}'", 
                    searchSource, searchDest, rideSource, rideDest);
                
                // Check if source and destination text match (partial match is OK)
                // Try both directions: search text in ride text, and ride text in search text
                boolean sourceMatches = searchSource.isEmpty() || 
                    rideSource.contains(searchSource) || 
                    searchSource.contains(rideSource) ||
                    rideSource.startsWith(searchSource) ||
                    searchSource.startsWith(rideSource) ||
                    // Also check if the core location name matches (ignoring state/country suffixes)
                    extractCoreLocationName(rideSource).equals(extractCoreLocationName(searchSource));
                boolean destMatches = searchDest.isEmpty() || 
                    rideDest.contains(searchDest) || 
                    searchDest.contains(rideDest) ||
                    rideDest.startsWith(searchDest) ||
                    searchDest.startsWith(rideDest) ||
                    // Also check if the core location name matches (ignoring state/country suffixes)
                    extractCoreLocationName(rideDest).equals(extractCoreLocationName(searchDest));
                boolean exactTextMatch = sourceMatches && destMatches;
                
                log.info("   Text match result - Source matches: {}, Dest matches: {}, Exact text match: {}", 
                    sourceMatches, destMatches, exactTextMatch);
                
                if (exactTextMatch) {
                    log.info("✅ Exact text match found for ride {}: '{}' -> '{}' (search: '{}' -> '{}')", 
                        ride.getId(), ride.getSource(), ride.getDestination(), 
                        request.getSource(), request.getDestination());
                    return true;
                }
                
                // CRITICAL: Check for partial route matches using coordinates (geocoded if needed)
                // This is the KEY logic for partial matching - MUST run even if text match fails
                log.info("   Text match failed, checking coordinate-based partial matching...");
                log.info("   Available coordinates - Source: [lat={}, lon={}], Dest: [lat={}, lon={}]", 
                    finalSourceLat, finalSourceLon, finalDestLat, finalDestLon);
                
                if (finalSourceLat != null && finalSourceLon != null &&
                    finalDestLat != null && finalDestLon != null) {
                    
                    log.info("🔍🔍🔍 Checking coordinate-based matching for ride {}: {} -> {} (passenger: {} -> {})", 
                        ride.getId(), ride.getSource(), ride.getDestination(),
                        request.getSource(), request.getDestination());
                    log.info("   Passenger coordinates - Source: [lat={}, lon={}], Destination: [lat={}, lon={}]", 
                        finalSourceLat, finalSourceLon, finalDestLat, finalDestLon);
                    log.info("   Ride {} has geometry: {}", ride.getId(), 
                        (ride.getRouteGeometry() != null && !ride.getRouteGeometry().trim().isEmpty()) ? "YES" : "NO");
                    
                    // FIX B: Check geometry length to detect truncation
                    if (ride.getRouteGeometry() != null && !ride.getRouteGeometry().trim().isEmpty()) {
                        int geometryLength = ride.getRouteGeometry().length();
                        log.debug("   Geometry length: {} chars", geometryLength);
                        // If geometry is suspiciously short (< 100 chars), it might be truncated
                        if (geometryLength < 100) {
                            log.warn("   ⚠️ Route geometry is very short ({} chars) - might be truncated! " +
                                    "Check database column type (should be TEXT/CLOB, not VARCHAR)", geometryLength);
                        }
                    }
                    
                    // Prepare passenger coordinates
                    // CRITICAL: Format is [longitude, latitude] - must match polyline format
                    double[] passengerSource = new double[]{finalSourceLon, finalSourceLat};
                    double[] passengerDestination = new double[]{finalDestLon, finalDestLat};
                    
                    // PRIORITY 1: Check if ride has stored route geometry (most accurate)
                    if (ride.getRouteGeometry() != null && !ride.getRouteGeometry().trim().isEmpty()) {
                        log.info("   ✅ Ride {} has stored geometry (length: {} chars), using polyline matching", 
                            ride.getId(), ride.getRouteGeometry().length());
                        try {
                            boolean polylineMatch = routeGeometryUtil.isPassengerRouteAlongDriverPolyline(
                                passengerSource,
                                passengerDestination,
                                ride.getRouteGeometry()
                            );
                            if (polylineMatch) {
                                log.info("✅✅✅ POLYLINE MATCH FOUND for ride {}: {} -> {} (passenger: {} -> {})", 
                                    ride.getId(), ride.getSource(), ride.getDestination(),
                                    request.getSource(), request.getDestination());
                                return true;
                            } else {
                                log.info("   ❌ Polyline match failed for ride {} - checking coordinate fallback", ride.getId());
                            }
                        } catch (Exception ex) {
                            log.error("   ⚠️ Error in polyline matching for ride {}: {}", ride.getId(), ex.getMessage(), ex);
                        }
                    } else {
                        log.info("   ⚠️ Ride {} has NO stored geometry (null or empty), generating synthetic polyline for matching", ride.getId());
                        
                        // PRIORITY 2: Generate synthetic polyline and use polyline matching
                        try {
                            // Geocode driver's source and destination to get coordinates
                            String[] driverSourceCoords = googleMapsService.geocodeAddress(ride.getSource());
                            String[] driverDestCoords = googleMapsService.geocodeAddress(ride.getDestination());
                            
                            if (driverSourceCoords != null && driverSourceCoords.length >= 2 &&
                                driverDestCoords != null && driverDestCoords.length >= 2) {
                                
                                double driverSourceLat = Double.parseDouble(driverSourceCoords[1]);
                                double driverSourceLon = Double.parseDouble(driverSourceCoords[0]);
                                double driverDestLat = Double.parseDouble(driverDestCoords[1]);
                                double driverDestLon = Double.parseDouble(driverDestCoords[0]);
                                
                                // Generate synthetic polyline with intermediate waypoints
                                List<double[]> syntheticPolyline = routeGeometryUtil.generateSyntheticPolyline(
                                    driverSourceLat, driverSourceLon,
                                    driverDestLat, driverDestLon,
                                    syntheticWaypoints
                                );
                                
                                // Convert synthetic polyline to JSON format for matching
                                String syntheticGeometryJson = objectMapper.writeValueAsString(
                                    syntheticPolyline.stream()
                                        .map(point -> new double[]{point[0], point[1]}) // [lon, lat]
                                        .collect(Collectors.toList())
                                );
                                
                                log.info("   ✅ Generated synthetic polyline with {} waypoints, using polyline matching", syntheticWaypoints);
                                
                                // Use polyline matching with synthetic geometry
                                boolean syntheticPolylineMatch = routeGeometryUtil.isPassengerRouteAlongDriverPolyline(
                                    passengerSource,
                                    passengerDestination,
                                    syntheticGeometryJson
                                );
                                
                                if (syntheticPolylineMatch) {
                                    log.info("✅✅✅ SYNTHETIC POLYLINE MATCH FOUND for ride {}: {} -> {} (passenger: {} -> {})", 
                                        ride.getId(), ride.getSource(), ride.getDestination(),
                                        request.getSource(), request.getDestination());
                                    return true;
                                } else {
                                    log.info("   ❌ Synthetic polyline match failed for ride {} - checking coordinate fallback", ride.getId());
                                }
                            } else {
                                log.warn("   ⚠️ Failed to geocode driver route for synthetic polyline generation");
                            }
                        } catch (Exception ex) {
                            log.error("   ⚠️ Error generating synthetic polyline for ride {}: {}", ride.getId(), ex.getMessage(), ex);
                        }
                    }
                    
                    // PRIORITY 3: Final fallback to simple coordinate-based matching (for very old rides or API failures)
                    log.info("   Checking final coordinate-based fallback matching for ride {}", ride.getId());
                    boolean coordinateMatch = isPassengerRouteAlongDriverJourney(
                        finalSourceLat, finalSourceLon,
                        finalDestLat, finalDestLon,
                        ride.getSource(), ride.getDestination()
                    );
                    if (coordinateMatch) {
                        log.info("✅✅✅ COORDINATE-BASED MATCH FOUND for ride {}: {} -> {} (passenger: {} -> {})", 
                            ride.getId(), ride.getSource(), ride.getDestination(),
                            request.getSource(), request.getDestination());
                        return true;
                    } else {
                        log.info("   ❌ Coordinate-based match also failed for ride {}", ride.getId());
                    }
                } else {
                    // FIX D: Better error handling - log clearly but don't silently skip
                    log.error("❌❌❌ CRITICAL: No valid coordinates available for ride {} - partial matching CANNOT work!", 
                        ride.getId());
                    log.error("   Source coords: lat={}, lon={}, Dest coords: lat={}, lon={}", 
                        finalSourceLat, finalSourceLon, finalDestLat, finalDestLon);
                    log.error("   This means geocoding failed or returned invalid coordinates for: '{}' or '{}'", 
                        request.getSource(), request.getDestination());
                    log.error("   Ride {} will NOT match because: 1) Text match failed, 2) No valid coordinates for partial matching", 
                        ride.getId());
                    log.error("   SUGGESTION: Check spelling, use autocomplete suggestions, or verify geocoding API is working");
                }
                
                log.info("❌❌❌ RIDE {} REJECTED - No match found (text match: {}, coordinates available: {})", 
                    ride.getId(), exactTextMatch, 
                    (finalSourceLat != null && finalSourceLon != null && finalDestLat != null && finalDestLon != null));
                return false;
            })
            .collect(Collectors.toList());
        
        int totalRidesForDate = rideRepository.findByRideDateAndStatusInAndAvailableSeatsGreaterThan(
            request.getRideDate(),
            Arrays.asList(RideStatus.POSTED, RideStatus.BOOKED),
            0
        ).size();
        
        log.info("✅✅✅ FINAL RESULT: Found {} rides matching search criteria (exact + partial matches) out of {} total rides for date {}", 
            rides.size(), totalRidesForDate, request.getRideDate());
        
        if (rides.isEmpty() && totalRidesForDate > 0) {
            log.error("⚠️⚠️⚠️ WARNING: {} rides exist for date {} but NONE matched the search criteria!", 
                totalRidesForDate, request.getRideDate());
            log.error("   Search: {} -> {}", request.getSource(), request.getDestination());
            log.error("   Possible reasons:");
            log.error("   1. Text matching failed AND coordinate matching failed");
            log.error("   2. Coordinates not available (geocoding failed)");
            log.error("   3. Rides don't have route geometry AND synthetic polyline generation failed");
            log.error("   4. Distance threshold too strict (current: {}m)", routeGeometryUtil.getMaxDistanceMeters());
        }
        
        // Convert to response DTOs with driver and vehicle details
        List<RideResponse> results = rides.stream()
            .map(ride -> buildRideResponseWithDetails(ride, authorization))
            .collect(Collectors.toList());
        
        log.info("📊 After building responses: {} rides", results.size());
        
        // Apply filters
        List<RideResponse> filteredResults = results.stream()
            .filter(ride -> {
                // Price filter
                if (request.getMinPrice() != null && ride.getTotalFare() != null) {
                    if (ride.getTotalFare() < request.getMinPrice()) {
                        log.debug("   Ride {} filtered out by minPrice: {} < {}", 
                            ride.getId(), ride.getTotalFare(), request.getMinPrice());
                        return false;
                    }
                }
                if (request.getMaxPrice() != null && ride.getTotalFare() != null) {
                    if (ride.getTotalFare() > request.getMaxPrice()) {
                        log.debug("   Ride {} filtered out by maxPrice: {} > {}", 
                            ride.getId(), ride.getTotalFare(), request.getMaxPrice());
                        return false;
                    }
                }
                
                // Vehicle type filter (partial match on model)
                if (request.getVehicleType() != null && !request.getVehicleType().trim().isEmpty()) {
                    String vehicleModel = ride.getVehicleModel();
                    if (vehicleModel == null || 
                        !vehicleModel.toLowerCase().contains(request.getVehicleType().toLowerCase().trim())) {
                        log.debug("   Ride {} filtered out by vehicleType: {} does not contain {}", 
                            ride.getId(), vehicleModel, request.getVehicleType());
                        return false;
                    }
                }
                
                // Driver rating filter - Note: Rating field not yet implemented
                // This filter will be enabled when rating system is integrated with Review Service
                // if (request.getMinRating() != null && ride.getDriverRating() != null) {
                //     if (ride.getDriverRating() < request.getMinRating()) {
                //         return false;
                //     }
                // }
                
                return true;
            })
            .collect(Collectors.toList());
        
        log.info("✅✅✅ FINAL FILTERED RESULT: {} rides after applying price/vehicle filters (from {} before filters)", 
            filteredResults.size(), results.size());
        
        return filteredResults;
    }
    
    /**
     * Check if passenger's route lies anywhere along driver's journey
     * 
     * Algorithm:
     * 1. A point lies on a line segment if the sum of distances from the point to both endpoints
     *    is approximately equal to the distance between the endpoints (within a threshold)
     * 2. Passenger's source must lie between driver's source and destination
     * 3. Passenger's destination must lie between driver's source and destination
     * 4. Passenger's source must be closer to driver's source than passenger's destination
     *    (ensuring correct order along the route)
     * 
     * @param passengerSourceLat Passenger source latitude
     * @param passengerSourceLon Passenger source longitude
     * @param passengerDestLat Passenger destination latitude
     * @param passengerDestLon Passenger destination longitude
     * @param driverSource Driver's source location (text - will be geocoded)
     * @param driverDest Driver's destination location (text - will be geocoded)
     * @return true if passenger's route lies along driver's journey
     */
    private boolean isPassengerRouteAlongDriverJourney(
            Double passengerSourceLat, Double passengerSourceLon,
            Double passengerDestLat, Double passengerDestLon,
            String driverSource, String driverDest) {
        
        try {
            // Geocode driver's source and destination
            String[] driverSourceCoords = googleMapsService.geocodeAddress(driverSource);
            String[] driverDestCoords = googleMapsService.geocodeAddress(driverDest);
            
            if (driverSourceCoords == null || driverSourceCoords.length != 2 ||
                driverDestCoords == null || driverDestCoords.length != 2) {
                log.warn("Failed to geocode driver route: {} -> {}", driverSource, driverDest);
                return false; // Can't determine route without coordinates
            }
            
            double driverSourceLat = Double.parseDouble(driverSourceCoords[1]);
            double driverSourceLon = Double.parseDouble(driverSourceCoords[0]);
            double driverDestLat = Double.parseDouble(driverDestCoords[1]);
            double driverDestLon = Double.parseDouble(driverDestCoords[0]);
            
            // Distance of driver's full journey (line segment length)
            double driverJourneyDistance = calculateHaversineDistance(
                driverSourceLat, driverSourceLon,
                driverDestLat, driverDestLon
            );
            
            if (driverJourneyDistance <= 0) {
                log.warn("Invalid driver journey distance: {}", driverJourneyDistance);
                return false;
            }
            
            // Distance from passenger source to driver source
            double distPassengerSourceToDriverSource = calculateHaversineDistance(
                passengerSourceLat, passengerSourceLon,
                driverSourceLat, driverSourceLon
            );
            
            // Distance from passenger source to driver destination
            double distPassengerSourceToDriverDest = calculateHaversineDistance(
                passengerSourceLat, passengerSourceLon,
                driverDestLat, driverDestLon
            );
            
            // Distance from passenger destination to driver source
            double distPassengerDestToDriverSource = calculateHaversineDistance(
                passengerDestLat, passengerDestLon,
                driverSourceLat, driverSourceLon
            );
            
            // Distance from passenger destination to driver destination
            double distPassengerDestToDriverDest = calculateHaversineDistance(
                passengerDestLat, passengerDestLon,
                driverDestLat, driverDestLon
            );
            
            // Distance of passenger's journey
            double passengerJourneyDistance = calculateHaversineDistance(
                passengerSourceLat, passengerSourceLon,
                passengerDestLat, passengerDestLon
            );
            
            // Threshold: Maximum distance a point can be from the route line segment
            // Use configurable threshold (default: 30km) for coordinate-based fallback
            // This allows for reasonable detours, nearby locations, and route variations
            // Note: maxDistanceFromRouteKm is injected from @Value annotation
            
            // CRITICAL ALGORITHM: Check if a point lies on a line segment
            // A point P lies on line segment AB if: |AP| + |PB| ≈ |AB| (within threshold)
            // This means the sum of distances from point to endpoints equals the segment length
            
            // Check if passenger source lies on driver's route (between driver source and destination)
            double sumDistPassengerSource = distPassengerSourceToDriverSource + distPassengerSourceToDriverDest;
            double diffPassengerSource = Math.abs(sumDistPassengerSource - driverJourneyDistance);
            // Point is on route if sum of distances ≈ route length (within threshold)
            boolean passengerSourceOnRoute = diffPassengerSource <= maxDistanceFromRouteKm;
            
            // Also check if passenger source is very close to driver source or destination (exact/endpoint match)
            boolean passengerSourceAtEndpoint = distPassengerSourceToDriverSource <= maxDistanceFromRouteKm ||
                                               distPassengerSourceToDriverDest <= maxDistanceFromRouteKm;
            
            // Passenger source is along route if it's on the route OR at an endpoint
            boolean passengerSourceAlongRoute = passengerSourceOnRoute || passengerSourceAtEndpoint;
            
            // Check if passenger destination lies on driver's route (between driver source and destination)
            double sumDistPassengerDest = distPassengerDestToDriverSource + distPassengerDestToDriverDest;
            double diffPassengerDest = Math.abs(sumDistPassengerDest - driverJourneyDistance);
            boolean passengerDestOnRoute = diffPassengerDest <= maxDistanceFromRouteKm;
            
            // Also check if passenger destination is very close to driver source or destination (exact/endpoint match)
            boolean passengerDestAtEndpoint = distPassengerDestToDriverSource <= maxDistanceFromRouteKm ||
                                             distPassengerDestToDriverDest <= maxDistanceFromRouteKm;
            
            boolean passengerDestAlongRoute = passengerDestOnRoute || passengerDestAtEndpoint;
            
            // CRITICAL: Check that passenger's source comes BEFORE passenger's destination along driver's route
            // Passenger source should be closer to driver source than passenger destination is
            // This ensures correct order: Driver Source -> Passenger Source -> Passenger Dest -> Driver Dest
            // Allow some flexibility (10km) for route variations and curved roads
            boolean correctOrder = distPassengerSourceToDriverSource <= distPassengerDestToDriverSource + 10.0;
            
            // Additional check: Passenger's journey should be a reasonable subset of driver's journey
            // Allow up to 1.5x driver's journey length to account for route variations
            boolean reasonableJourneyLength = passengerJourneyDistance <= driverJourneyDistance * 1.5;
            
            // Edge case 1: Exact match - passenger route exactly matches driver route
            // (both endpoints are very close to driver endpoints)
            // Increased threshold to 10km to catch more exact matches
            boolean exactMatch = distPassengerSourceToDriverSource <= 10.0 &&
                                distPassengerDestToDriverDest <= 10.0;
            if (exactMatch) {
                log.info("   ✅ Exact match detected (passenger route matches driver route) - source: {}km, dest: {}km", 
                    String.format("%.2f", distPassengerSourceToDriverSource),
                    String.format("%.2f", distPassengerDestToDriverDest));
                return true;
            }
            
            // Edge case 2: Reverse exact match - passenger route is reverse of driver route
            // (passenger source = driver dest, passenger dest = driver source)
            boolean reverseMatch = distPassengerSourceToDriverDest <= 10.0 &&
                                  distPassengerDestToDriverSource <= 10.0;
            if (reverseMatch) {
                log.info("   ✅ Reverse match detected (passenger route is reverse of driver route)");
                return true;
            }
            
            // Edge case 3: Passenger source matches driver source exactly (within 10km)
            // and destination is along the route
            if (distPassengerSourceToDriverSource <= 10.0 && passengerDestAlongRoute) {
                log.info("   ✅ Match: Passenger starts at driver source, destination along route");
                return true;
            }
            
            // Edge case 4: Passenger destination matches driver destination exactly (within 10km)
            // and source is along the route
            if (distPassengerDestToDriverDest <= 10.0 && passengerSourceAlongRoute) {
                log.info("   ✅ Match: Passenger ends at driver destination, source along route");
                return true;
            }
            
            // Edge case 5: If passenger's journey is very short compared to driver's, be more lenient
            // This handles cases where passenger travels a small segment of a long driver route
            boolean isShortSegment = passengerJourneyDistance <= driverJourneyDistance * 0.3;
            if (isShortSegment) {
                // For short segments, only require that at least one endpoint is on route
                // and the order is correct
                boolean shortSegmentMatch = (passengerSourceAlongRoute || passengerDestAlongRoute) &&
                                           correctOrder &&
                                           reasonableJourneyLength;
                if (shortSegmentMatch) {
                    log.info("   ✅ Short segment match detected (passenger journey is {}% of driver journey)",
                        String.format("%.1f", (passengerJourneyDistance / driverJourneyDistance) * 100));
                    return true;
                }
            }
            
            // Standard case: Passenger's route lies along driver's journey if:
            // 1. Both passenger source and destination are along the route, AND
            // 2. Passenger source comes before passenger destination (correct order), AND
            // 3. Passenger's journey length is reasonable (subset of driver's journey)
            boolean isAlongRoute = passengerSourceAlongRoute && 
                                  passengerDestAlongRoute && 
                                  correctOrder &&
                                  reasonableJourneyLength;
            
            log.info("🔍 Route Matching - Driver: {}->{} ({}km), Passenger: {}->{} ({}km)",
                driverSource, driverDest, String.format("%.2f", driverJourneyDistance),
                "passenger", "passenger", String.format("%.2f", passengerJourneyDistance));
            log.info("   Passenger Source on route: {} (diff: {}km), Passenger Dest on route: {} (diff: {}km), Order correct: {}, Match: {}",
                passengerSourceAlongRoute, String.format("%.2f", diffPassengerSource),
                passengerDestAlongRoute, String.format("%.2f", diffPassengerDest),
                correctOrder, isAlongRoute);
            
            return isAlongRoute;
            
        } catch (Exception ex) {
            log.error("Error checking route matching: {}", ex.getMessage(), ex);
            return false; // On error, don't match
        }
    }
    
    /**
     * Calculate Haversine distance between two coordinates (straight-line distance in km)
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Get ride details by ID
     * @param rideId Ride ID
     * @param authorization Optional JWT token for fetching driver/vehicle details
     * @return RideResponse with ride details
     */
    @Transactional
    public RideResponse getRideById(Long rideId, String authorization) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Note: buildRideResponseWithDetails may backfill and save denormalized data
        return buildRideResponseWithDetails(ride, authorization);
    }
    
    /**
     * Book a seat on a ride
     * @param rideId Ride ID
     * @param passengerId User's ID (cannot book their own ride)
     * @param request Booking request
     * @param authorization JWT token for User Service calls
     * @return BookingResponse with booking details
     */
    public BookingResponse bookSeat(Long rideId, Long passengerId, BookingRequest request, String authorization) {
        // Get ride
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Validate ride is available
        if (ride.getStatus() == RideStatus.CANCELLED || ride.getStatus() == RideStatus.COMPLETED) {
            throw new BadRequestException("Ride is not available for booking");
        }
        
        // CRITICAL: Check if scheduled ride time has passed - passengers can only book before the scheduled time
        LocalDateTime scheduledDateTime = LocalDateTime.of(ride.getRideDate(), ride.getRideTime());
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(scheduledDateTime) || currentDateTime.isEqual(scheduledDateTime)) {
            throw new BadRequestException("Cannot book ride: The scheduled departure time (" + 
                ride.getRideDate() + " at " + ride.getRideTime() + ") has already passed. " +
                "Bookings are only allowed before the scheduled ride time.");
        }
        
        // Check if passenger is trying to book their own ride
        if (ride.getDriverId().equals(passengerId)) {
            throw new BadRequestException("Driver cannot book their own ride");
        }
        
        // Check if passenger already has a booking for this ride
        Optional<Booking> existingBooking = bookingRepository.findByRideAndPassengerId(ride, passengerId);
        if (existingBooking.isPresent() && 
            (existingBooking.get().getStatus() == BookingStatus.PENDING || 
             existingBooking.get().getStatus() == BookingStatus.CONFIRMED)) {
            throw new BadRequestException("Passenger already has an active booking for this ride");
        }
        
        // Validate available seats
        if (ride.getAvailableSeats() < request.getSeatsBooked()) {
            throw new BadRequestException("Not enough seats available. Available: " + ride.getAvailableSeats());
        }
        
        // Calculate passenger-specific fare
        FareCalculationResponse passengerFareResponse;
        try {
            // OPTIMIZATION: If passenger is taking the full route (no custom source/destination),
            // use the ride's stored fare instead of recalculating (avoids API calls and potential failures)
            String passengerSource = (request.getPassengerSource() != null && !request.getPassengerSource().trim().isEmpty()) 
                    ? request.getPassengerSource().trim() 
                    : null;
            String passengerDestination = (request.getPassengerDestination() != null && !request.getPassengerDestination().trim().isEmpty()) 
                    ? request.getPassengerDestination().trim() 
                    : null;
            
            boolean isFullRoute = (passengerSource == null || passengerSource.equalsIgnoreCase(ride.getSource())) &&
                                 (passengerDestination == null || passengerDestination.equalsIgnoreCase(ride.getDestination()));
            
            if (isFullRoute && ride.getTotalFare() != null && ride.getDistanceKm() != null) {
                // Use stored ride fare for full route (no API call needed)
                // CRITICAL: ride.getTotalFare() is the fare for ONE passenger seat, not the entire ride
                log.info("✅ Passenger taking full route, using stored ride fare: {} {} (per seat)", 
                    ride.getTotalFare(), ride.getCurrency());
                passengerFareResponse = new FareCalculationResponse();
                passengerFareResponse.setDistanceKm(ride.getDistanceKm());
                passengerFareResponse.setBaseFare(ride.getBaseFare() != null ? ride.getBaseFare() : 50.0);
                passengerFareResponse.setRatePerKm(ride.getRatePerKm() != null ? ride.getRatePerKm() : 10.0);
                passengerFareResponse.setTotalFare(ride.getTotalFare()); // This is fare for ONE seat
                passengerFareResponse.setCurrency(ride.getCurrency() != null ? ride.getCurrency() : "INR");
                // Duration not stored in ride, but not critical for booking
                passengerFareResponse.setEstimatedDurationSeconds(null);
                passengerFareResponse.setEstimatedDurationText(null);
            } else {
                // Calculate fare for partial route (passenger joins/exits mid-route)
                log.info("Passenger taking partial route, calculating fare: {} -> {}", 
                    passengerSource != null ? passengerSource : ride.getSource(),
                    passengerDestination != null ? passengerDestination : ride.getDestination());
                passengerFareResponse = fareCalculationService.calculatePassengerFare(
                        ride.getSource(),
                        ride.getDestination(),
                        passengerSource,
                        passengerDestination
                );
            }
            
            // CRITICAL: Log the fare per seat for debugging
            log.info("💰 Calculated fare per seat: {} {}", 
                passengerFareResponse.getTotalFare(), passengerFareResponse.getCurrency());
        } catch (Exception ex) {
            log.error("Failed to calculate passenger fare: {}", ex.getMessage(), ex);
            throw new BadRequestException("Failed to calculate passenger fare: " + ex.getMessage());
        }

        // Create booking with PENDING status (will be confirmed after payment verification)
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassengerId(passengerId);
        booking.setSeatsBooked(request.getSeatsBooked());
        booking.setStatus(BookingStatus.PENDING); // Changed to PENDING - will be CONFIRMED after payment
        booking.setPassengerSource(request.getPassengerSource());
        booking.setPassengerDestination(request.getPassengerDestination());
        booking.setPassengerDistanceKm(passengerFareResponse.getDistanceKm());
        // CRITICAL: Calculate total fare for all seats booked (fare per seat * number of seats)
        // The passengerFareResponse.getTotalFare() is the fare for ONE seat
        // We need to multiply by the number of seats booked
        // Store these in final variables that will be accessible later for validation
        final Double farePerSeat = passengerFareResponse.getTotalFare();
        final Integer seatsBooked = request.getSeatsBooked();
        final String currency = passengerFareResponse.getCurrency() != null ? passengerFareResponse.getCurrency() : "INR";
        
        // CRITICAL: Validate inputs before calculation
        if (farePerSeat == null || farePerSeat <= 0) {
            throw new BadRequestException("Invalid fare per seat: " + farePerSeat);
        }
        if (seatsBooked == null || seatsBooked <= 0) {
            throw new BadRequestException("Invalid number of seats: " + seatsBooked);
        }
        
        // CRITICAL: Calculate total fare for all seats (per-seat fare * number of seats)
        Double totalFareForAllSeats = farePerSeat * seatsBooked;
        
        // CRITICAL: Round to 2 decimal places to avoid floating point precision issues
        totalFareForAllSeats = Math.round(totalFareForAllSeats * 100.0) / 100.0;
        
        log.info("💰 Fare calculation - Per seat: {} {}, Seats booked: {}, Total fare (before platform fee): {} {}", 
            farePerSeat, currency, seatsBooked, totalFareForAllSeats, currency);
        
        // CRITICAL: Verify calculation is correct
        if (seatsBooked > 1 && totalFareForAllSeats <= farePerSeat) {
            log.error("❌ CRITICAL ERROR: Total fare ({}) is not greater than per-seat fare ({}) for {} seats! Calculation is wrong!", 
                totalFareForAllSeats, farePerSeat, seatsBooked);
            throw new RuntimeException("Fare calculation error: Total fare must be greater than per-seat fare for multiple seats");
        }
        
        // CRITICAL: Store final calculated value for later use (booking save, payment request, response validation)
        final Double finalTotalFareForAllSeats = totalFareForAllSeats;
        
        // CRITICAL: Set the total fare (per-seat * seats) in booking BEFORE saving
        // This ensures the booking record reflects the actual amount charged
        booking.setPassengerFare(finalTotalFareForAllSeats);
        booking.setCurrency(currency);
        
        log.info("💰 Booking fare calculation - Per seat: {} {}, Seats: {}, Total fare stored in booking: {} {}", 
            farePerSeat, currency, seatsBooked, finalTotalFareForAllSeats, currency);
        
        // CRITICAL: Save and flush to ensure the value is persisted immediately
        booking = bookingRepository.save(booking);
        bookingRepository.flush(); // Force flush to database
        
        // CRITICAL: Verify the saved booking has correct fare by re-reading from database
        // This ensures we're working with the actual persisted value
        Booking savedBooking = bookingRepository.findById(booking.getId())
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved booking"));
        
        log.info("✅ Booking saved - ID: {}, Seats: {}, PassengerFare (from DB): {} {}", 
            savedBooking.getId(), savedBooking.getSeatsBooked(), savedBooking.getPassengerFare(), savedBooking.getCurrency());
        
        // CRITICAL: Validate that the saved booking has the correct fare
        // If it doesn't match, there's a serious issue - log error and force correction
        if (savedBooking.getPassengerFare() != null && finalTotalFareForAllSeats != null) {
            double savedFareDiff = Math.abs(savedBooking.getPassengerFare() - finalTotalFareForAllSeats);
            if (savedFareDiff > 0.01) {
                log.error("❌ CRITICAL ERROR: Saved booking fare ({}) does NOT match calculated fare ({})! Difference: {}", 
                    savedBooking.getPassengerFare(), finalTotalFareForAllSeats, savedFareDiff);
                log.error("❌ This indicates a database persistence issue. Forcing correction...");
                // CRITICAL: Force update the booking with the correct value
                savedBooking.setPassengerFare(finalTotalFareForAllSeats);
                savedBooking = bookingRepository.save(savedBooking);
                bookingRepository.flush();
                log.warn("⚠️ Corrected booking fare in database from {} to {}", 
                    savedBooking.getPassengerFare(), finalTotalFareForAllSeats);
            } else {
                log.info("✅ Verified: Saved booking fare ({}) matches calculated fare ({})", 
                    savedBooking.getPassengerFare(), finalTotalFareForAllSeats);
            }
        }
        
        // CRITICAL: Use the saved booking from database to ensure consistency
        booking = savedBooking;
        
        // CRITICAL: Initiate payment through Payment Service
        // Payment MUST be initiated before returning booking response
        // Frontend expects paymentOrder in response to open payment dialog
        Long paymentId = null;
        Map<String, Object> paymentOrderResponse = null;
        try {
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("bookingId", booking.getId());
            paymentRequest.put("passengerId", passengerId);
            paymentRequest.put("driverId", ride.getDriverId());
            // CRITICAL: Send total fare for all seats, not per-seat fare
            // Use Double.valueOf to ensure proper type
            paymentRequest.put("amount", Double.valueOf(finalTotalFareForAllSeats));
            paymentRequest.put("fare", Double.valueOf(finalTotalFareForAllSeats));
            paymentRequest.put("currency", currency);
            
            // CRITICAL: Log the exact values being sent
            log.info("🔔 Initiating payment for bookingId={}, seats={}, farePerSeat={} {}, totalFareForAllSeats={} {}", 
                booking.getId(), seatsBooked, farePerSeat, currency, finalTotalFareForAllSeats, currency);
            log.info("🔔 Payment request Map contents: bookingId={}, passengerId={}, driverId={}, amount={}, fare={}, currency={}", 
                paymentRequest.get("bookingId"), paymentRequest.get("passengerId"), paymentRequest.get("driverId"),
                paymentRequest.get("amount"), paymentRequest.get("fare"), paymentRequest.get("currency"));
            
            // CRITICAL: Double-check the Map values before sending
            Object mapAmount = paymentRequest.get("amount");
            Object mapFare = paymentRequest.get("fare");
            log.info("🔍 Payment request Map type check - amount type: {}, value: {}, fare type: {}, value: {}", 
                mapAmount != null ? mapAmount.getClass().getName() : "null", mapAmount,
                mapFare != null ? mapFare.getClass().getName() : "null", mapFare);
            
            paymentOrderResponse = paymentServiceClient.initiatePayment(paymentRequest);
            
            log.info("📦 Payment service response received: {}", paymentOrderResponse);
            
            if (paymentOrderResponse != null) {
                // CRITICAL: Convert Map to ensure all fields are properly structured
                // Feign may return the response as a Map, but we need to ensure all fields are present
                Map<String, Object> validatedPaymentOrder = new HashMap<>();
                
                // Extract and validate all required fields
                Object paymentIdObj = paymentOrderResponse.get("paymentId");
                if (paymentIdObj != null) {
                    if (paymentIdObj instanceof Number) {
                        paymentId = ((Number) paymentIdObj).longValue();
                    } else if (paymentIdObj instanceof String) {
                        paymentId = Long.parseLong((String) paymentIdObj);
                    }
                    validatedPaymentOrder.put("paymentId", paymentId);
                    booking.setPaymentId(paymentId);
                    booking = bookingRepository.save(booking);
                    log.info("✅ Payment ID saved to booking: {}", paymentId);
                } else {
                    log.error("❌ Payment response missing paymentId field! Available keys: {}", paymentOrderResponse.keySet());
                    throw new RuntimeException("Payment response missing paymentId");
                }
                
                // Extract orderId (handle both camelCase and snake_case)
                Object orderIdObj = paymentOrderResponse.get("orderId");
                if (orderIdObj == null) {
                    orderIdObj = paymentOrderResponse.get("order_id");
                }
                if (orderIdObj != null) {
                    validatedPaymentOrder.put("orderId", orderIdObj.toString());
                    validatedPaymentOrder.put("order_id", orderIdObj.toString()); // Support both formats
                } else {
                    log.error("❌ Payment response missing orderId! Available keys: {}", paymentOrderResponse.keySet());
                    throw new RuntimeException("Payment response missing orderId");
                }
                
                // Extract keyId
                Object keyIdObj = paymentOrderResponse.get("keyId");
                if (keyIdObj == null) {
                    keyIdObj = paymentOrderResponse.get("key_id");
                }
                if (keyIdObj != null) {
                    validatedPaymentOrder.put("keyId", keyIdObj.toString());
                } else {
                    log.error("❌ Payment response missing keyId! Available keys: {}", paymentOrderResponse.keySet());
                    throw new RuntimeException("Payment response missing keyId");
                }
                
                // Extract amount
                Object amountObj = paymentOrderResponse.get("amount");
                if (amountObj != null) {
                    validatedPaymentOrder.put("amount", amountObj);
                } else {
                    log.error("❌ Payment response missing amount! Available keys: {}", paymentOrderResponse.keySet());
                    throw new RuntimeException("Payment response missing amount");
                }
                
                // Extract currency
                Object currencyObj = paymentOrderResponse.get("currency");
                if (currencyObj != null) {
                    validatedPaymentOrder.put("currency", currencyObj.toString());
                } else {
                    validatedPaymentOrder.put("currency", "INR"); // Default
                }
                
                // Extract bookingId
                Object bookingIdObj = paymentOrderResponse.get("bookingId");
                if (bookingIdObj != null) {
                    validatedPaymentOrder.put("bookingId", bookingIdObj);
                } else {
                    validatedPaymentOrder.put("bookingId", booking.getId());
                }
                
                // Use validated payment order
                paymentOrderResponse = validatedPaymentOrder;
                
                log.info("✅ Payment initiated successfully: paymentId={}, orderId={}, keyId={}, amount={}, currency={}", 
                    paymentId, 
                    paymentOrderResponse.get("orderId"),
                    paymentOrderResponse.get("keyId"),
                    paymentOrderResponse.get("amount"),
                    paymentOrderResponse.get("currency"));
            } else {
                log.error("❌ Payment initiation returned null response!");
                throw new RuntimeException("Payment service returned null response");
            }
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to initiate payment for bookingId={}: {}", booking.getId(), e.getMessage(), e);
            // CRITICAL: Payment is required - booking cannot proceed without payment
            // Delete the booking if payment fails
            try {
                bookingRepository.delete(booking);
                log.info("🗑️ Deleted booking {} due to payment initiation failure", booking.getId());
            } catch (Exception deleteEx) {
                log.error("Failed to delete booking after payment failure: {}", deleteEx.getMessage());
            }
            throw new BadRequestException("Failed to initiate payment: " + e.getMessage() + ". Booking was not created.");
        }
        
        // Note: We don't update ride seats or status yet - this happens after payment verification
        // This ensures seats are only reserved after successful payment
        // Also, we don't send confirmation emails yet - they will be sent after payment verification
        
        // Get passenger profile from logged-in user account (needed for response)
        Map<String, Object> passengerProfile;
        try {
            passengerProfile = userServiceClient.getUserProfile(authorization);
        } catch (Exception e) {
            log.warn("Failed to fetch passenger profile: {}", e.getMessage());
            passengerProfile = new HashMap<>();
        }
        
        // Build booking response with payment order details
        BookingResponse bookingResponse = buildBookingResponse(booking, passengerProfile, null);
        
        // CRITICAL: Final validation - ensure booking response has correct passengerFare
        // It should be totalFareForBooking (fare per seat * seats), not per-seat fare
        // Use the final calculated values from earlier in the method
        // CRITICAL: Use finalTotalFareForAllSeats which is the same as totalFareForBooking
        // Both represent: farePerSeat * seatsBooked
        if (bookingResponse.getPassengerFare() != null && bookingResponse.getSeatsBooked() != null && 
            bookingResponse.getSeatsBooked() > 1) {
            Double responseFare = bookingResponse.getPassengerFare();
            Integer responseSeats = bookingResponse.getSeatsBooked();
            Double farePerSeatFromResponse = responseFare / responseSeats;
            
            // CRITICAL: Compare with the correct calculated values
            // farePerSeat is the per-seat fare, finalTotalFareForAllSeats is the total
            double farePerSeatDiff = Math.abs(farePerSeatFromResponse - farePerSeat);
            double totalFareDiff = Math.abs(responseFare - finalTotalFareForAllSeats);
            
            // If the response fare doesn't match the calculated total, it's wrong
            if (totalFareDiff > 0.01) {
                log.error("❌ CRITICAL: Booking response passengerFare mismatch! Response fare: {} ({} per seat), Expected total: {} ({} per seat) for {} seats", 
                    responseFare, farePerSeatFromResponse, finalTotalFareForAllSeats, farePerSeat, responseSeats);
                log.error("❌ Fare per seat difference: {}, Total fare difference: {}", farePerSeatDiff, totalFareDiff);
                // CRITICAL: Override with correct value to ensure frontend gets correct fare
                bookingResponse.setPassengerFare(finalTotalFareForAllSeats);
                log.warn("⚠️ Corrected booking response passengerFare from {} to {}", responseFare, finalTotalFareForAllSeats);
            } else {
                log.info("✅ Booking response passengerFare is correct: {} ({} per seat) for {} seats", 
                    responseFare, farePerSeatFromResponse, responseSeats);
            }
        } else if (bookingResponse.getSeatsBooked() != null && bookingResponse.getSeatsBooked() == 1) {
            // For single seat, passengerFare should equal farePerSeat
            log.debug("📋 Single seat booking - passengerFare: {}", bookingResponse.getPassengerFare());
        }
        
        // CRITICAL: Verify booking response has correct passengerFare before sending
        log.info("📋 Booking Response - ID: {}, Seats: {}, PassengerFare: {} {}, PaymentOrderAmount: {} paise", 
            bookingResponse.getId(), 
            bookingResponse.getSeatsBooked(),
            bookingResponse.getPassengerFare(),
            bookingResponse.getCurrency(),
            paymentOrderResponse != null ? paymentOrderResponse.get("amount") : "N/A");
        
        // CRITICAL: Payment order MUST be added to response for frontend to open payment dialog
        // paymentOrderResponse is already validated and normalized above
        if (paymentOrderResponse != null && paymentId != null) {
            bookingResponse.setPaymentId(paymentId);
            bookingResponse.setPaymentOrder(paymentOrderResponse); // Already validated and normalized
            
            // CRITICAL: Final validation - ensure amounts are consistent
            Object paymentAmountObj = paymentOrderResponse.get("amount");
            Long paymentAmountPaise = paymentAmountObj instanceof Number 
                ? ((Number) paymentAmountObj).longValue() 
                : (paymentAmountObj instanceof String ? Long.parseLong((String) paymentAmountObj) : null);
            Double paymentAmountRupees = paymentAmountPaise != null ? paymentAmountPaise / 100.0 : null;
            
            log.info("✅ Payment order added to booking response: paymentId={}, orderId={}, keyId={}, amount={} paise ({} {}), currency={}", 
                paymentId, 
                paymentOrderResponse.get("orderId"),
                paymentOrderResponse.get("keyId"),
                paymentAmountPaise,
                paymentAmountRupees,
                paymentOrderResponse.get("currency"));
            
            // CRITICAL: Validate fare consistency
            if (bookingResponse.getPassengerFare() != null && paymentAmountRupees != null) {
                // Platform fee should be 10% of fare
                Double expectedPlatformFee = bookingResponse.getPassengerFare() * 0.10;
                Double expectedTotal = bookingResponse.getPassengerFare() + expectedPlatformFee;
                Double actualTotal = paymentAmountRupees;
                
                log.info("💰 Fare Validation - PassengerFare: {} {}, Expected Platform Fee: {} {}, Expected Total: {} {}, Actual Total: {} {}", 
                    bookingResponse.getPassengerFare(), bookingResponse.getCurrency(),
                    expectedPlatformFee, bookingResponse.getCurrency(),
                    expectedTotal, bookingResponse.getCurrency(),
                    actualTotal, paymentOrderResponse.get("currency"));
                
                // Allow small rounding differences (0.01)
                if (Math.abs(expectedTotal - actualTotal) > 0.02) {
                    log.warn("⚠️ WARNING: Payment amount mismatch! Expected: {} {}, Actual: {} {}", 
                        expectedTotal, bookingResponse.getCurrency(), actualTotal, paymentOrderResponse.get("currency"));
                }
            }
        } else {
            log.error("❌ CRITICAL: Payment order NOT added to booking response! paymentOrderResponse={}, paymentId={}", 
                paymentOrderResponse, paymentId);
            // This should never happen if payment initiation succeeded (exception would have been thrown)
            throw new RuntimeException("Payment order missing in booking response - this should not happen");
        }
        
        log.info("✅ Booking created with PENDING status. Payment order in response: {}", 
            bookingResponse.getPaymentOrder() != null ? "YES" : "NO");
        
        // Final validation: ensure paymentOrder has orderId
        Map<String, Object> finalPaymentOrder = bookingResponse.getPaymentOrder();
        if (finalPaymentOrder != null) {
            Object finalOrderId = finalPaymentOrder.get("orderId") != null ? finalPaymentOrder.get("orderId") : finalPaymentOrder.get("order_id");
            if (finalOrderId == null) {
                log.error("❌ CRITICAL: Payment order missing orderId! Keys: {}", finalPaymentOrder.keySet());
            } else {
                log.info("✅ Final validation: Payment order has orderId={}", finalOrderId);
            }
        }
        
        // Send real-time notification to driver about new booking
        try {
            String passengerName = passengerProfile.get("name") != null ? 
                (String) passengerProfile.get("name") : "A passenger";
            notificationService.notifyDriverNewBooking(
                ride.getDriverId(),
                booking.getId(),
                passengerName,
                booking.getSeatsBooked(),
                ride.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send real-time notification to driver: {}", e.getMessage());
            // Don't fail booking if notification fails
        }
        
        return bookingResponse;
    }
    
    /**
     * Verify payment and confirm booking
     * Called by frontend after payment is completed
     * @param bookingId Booking ID
     * @param paymentVerificationRequest Payment verification request from frontend
     * @return Updated BookingResponse
     */
    public BookingResponse verifyPaymentAndConfirmBooking(Long bookingId, Map<String, Object> paymentVerificationRequest) {
        // Get booking
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        
        // Verify payment through Payment Service
        Map<String, Object> verificationResponse;
        try {
            verificationResponse = paymentServiceClient.verifyPayment(paymentVerificationRequest);
            
            Boolean verified = (Boolean) verificationResponse.get("verified");
            if (verified == null || !verified) {
                throw new BadRequestException("Payment verification failed: " + 
                    (verificationResponse.get("message") != null ? verificationResponse.get("message") : "Unknown error"));
            }
            
            log.info("Payment verified successfully for bookingId={}, paymentId={}", 
                bookingId, verificationResponse.get("paymentId"));
        } catch (Exception e) {
            log.error("Payment verification failed for bookingId={}: {}", bookingId, e.getMessage(), e);
            throw new BadRequestException("Payment verification failed: " + e.getMessage());
        }
        
        // Update booking status to CONFIRMED
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);
        
        // Update ride available seats (only after payment confirmation)
        Ride ride = booking.getRide();
        ride.setAvailableSeats(ride.getAvailableSeats() - booking.getSeatsBooked());
        
        // Update ride status if needed
        if (ride.getStatus() == RideStatus.POSTED) {
            ride.setStatus(RideStatus.BOOKED);
        }
        rideRepository.save(ride);
        
        log.info("Booking confirmed after payment verification: bookingId={}, rideId={}, seats={}", 
            booking.getId(), ride.getId(), booking.getSeatsBooked());
        
        // Get driver profile outside try-catch so it can be used for notifications
        Map<String, Object> driverProfile = null;
        try {
            driverProfile = userServiceClient.getUserPublicInfo(ride.getDriverId());
        } catch (Exception e) {
            log.warn("Failed to fetch driver profile for notifications: {}", e.getMessage());
        }
        
        // Send confirmation emails after payment verification
        try {
            // Get passenger profile
            Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
            
            // Prepare ride details for email
            Map<String, Object> rideDetails = new HashMap<>();
            rideDetails.put("source", ride.getSource());
            rideDetails.put("destination", ride.getDestination());
            rideDetails.put("rideDate", ride.getRideDate());
            rideDetails.put("rideTime", ride.getRideTime());
            rideDetails.put("vehicleModel", ride.getVehicleModel());
            rideDetails.put("vehicleLicensePlate", ride.getVehicleLicensePlate());
            
            // Extract passenger information
            String passengerName = passengerProfile.get("name") != null && !((String) passengerProfile.get("name")).isEmpty() ? 
                (String) passengerProfile.get("name") : "Passenger";
            String passengerEmail = passengerProfile.get("email") != null ? 
                ((String) passengerProfile.get("email")).trim() : null;
            if (passengerEmail != null && passengerEmail.isEmpty()) {
                passengerEmail = null;
            }
            String passengerPhone = passengerProfile.get("phone") != null ? 
                (String) passengerProfile.get("phone") : null;
            if (passengerPhone != null && passengerPhone.isEmpty()) {
                passengerPhone = null;
            }
            
            // Extract driver information
            String driverName = "Driver";
            if (driverProfile != null && driverProfile.get("name") != null) {
                String name = (String) driverProfile.get("name");
                if (!name.isEmpty()) {
                    driverName = name;
                }
            }
            if (driverName.equals("Driver") && ride.getDriverName() != null) {
                driverName = ride.getDriverName();
            }
            String driverEmail = null;
            if (driverProfile != null && driverProfile.get("email") != null) {
                driverEmail = ((String) driverProfile.get("email")).trim();
                if (driverEmail.isEmpty()) {
                driverEmail = null;
                }
            }
            
            // Send confirmation emails
            if (passengerEmail != null && !passengerEmail.isEmpty()) {
                try {
                    emailService.sendBookingConfirmationToPassenger(
                        passengerEmail,
                        passengerName,
                        driverName,
                        driverEmail != null ? driverEmail : "N/A",
                        rideDetails,
                        booking.getSeatsBooked()
                    );
                    log.info("Booking confirmation email sent to passenger: {}", passengerEmail);
                } catch (Exception e) {
                    log.error("Error sending email to passenger {}: {}", passengerEmail, e.getMessage(), e);
                }
            }
            
            if (driverEmail != null && !driverEmail.isEmpty()) {
                try {
                    emailService.sendBookingNotificationToDriver(
                        driverEmail,
                        driverName,
                        passengerName,
                        passengerEmail != null ? passengerEmail : "N/A",
                        passengerPhone,
                        rideDetails,
                        booking.getSeatsBooked()
                    );
                    log.info("Booking notification email sent to driver: {}", driverEmail);
                } catch (Exception e) {
                    log.error("Error sending email to driver {}: {}", driverEmail, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send confirmation emails: {}", e.getMessage());
            // Don't fail the booking if email fails
        }
        
        // Send real-time notification to passenger about booking confirmation
        try {
            String driverName = "Driver";
            if (driverProfile != null && driverProfile.get("name") != null) {
                String name = (String) driverProfile.get("name");
                if (!name.isEmpty()) {
                    driverName = name;
                }
            }
            if (driverName.equals("Driver") && ride.getDriverName() != null) {
                driverName = ride.getDriverName();
            }
            notificationService.notifyPassengerBookingConfirmed(
                booking.getPassengerId(),
                booking.getId(),
                ride.getId(),
                driverName
            );
        } catch (Exception e) {
            log.warn("Failed to send real-time notification to passenger: {}", e.getMessage());
            // Don't fail booking if notification fails
        }
        
        return buildBookingResponse(booking, null, ride);
    }
    
    /**
     * Update ride details
     * @param rideId Ride ID
     * @param driverId User's ID (must be the ride owner)
     * @param request Updated ride request
     * @param authorization JWT token
     * @return Updated RideResponse
     */
    public RideResponse updateRide(Long rideId, Long driverId, RideRequest request, String authorization) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can update the ride");
        }
        
        // Validate ride can be updated
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new BadRequestException("Cannot update a completed or cancelled ride");
        }
        
        // Get active bookings for notification purposes
        List<Booking> activeBookings = bookingRepository.findByRide(ride).stream()
            .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.PENDING)
            .collect(Collectors.toList());
        
        // Store old values to detect reschedule
        LocalDate oldDate = ride.getRideDate();
        LocalTime oldTime = ride.getRideTime();
        String oldSource = ride.getSource();
        String oldDestination = ride.getDestination();
        
        // Update ride details
        ride.setSource(request.getSource());
        ride.setDestination(request.getDestination());
        ride.setRideDate(request.getRideDate());
        ride.setRideTime(request.getRideTime());
        
        // Check if date or time changed (reschedule)
        boolean isReschedule = !oldDate.equals(request.getRideDate()) || !oldTime.equals(request.getRideTime());
        
        // If rescheduling with active bookings, notify passengers
        if (isReschedule && !activeBookings.isEmpty()) {
            String driverName = ride.getDriverName() != null ? ride.getDriverName() : "Driver";
            String newDateStr = request.getRideDate().toString();
            String newTimeStr = request.getRideTime().toString();
            
            // Prepare ride details for email
            Map<String, Object> rideDetails = new HashMap<>();
            rideDetails.put("source", ride.getSource());
            rideDetails.put("destination", ride.getDestination());
            rideDetails.put("vehicleModel", ride.getVehicleModel());
            rideDetails.put("vehicleLicensePlate", ride.getVehicleLicensePlate());
            
            log.info("Rescheduling ride {} - notifying {} passengers", ride.getId(), activeBookings.size());
            
            for (Booking booking : activeBookings) {
                // Get passenger information for email
                try {
                    Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
                    String passengerEmail = passengerProfile != null && passengerProfile.get("email") != null 
                        ? (String) passengerProfile.get("email") : null;
                    String passengerName = passengerProfile != null && passengerProfile.get("name") != null 
                        ? (String) passengerProfile.get("name") : "Passenger";
                    
                    // Send real-time notification to passenger about ride reschedule (ONCE)
                    try {
                        notificationService.notifyPassengerRideRescheduled(
                            booking.getPassengerId(),
                            ride.getId(),
                            driverName,
                            newDateStr,
                            newTimeStr
                        );
                        log.info("Sent real-time reschedule notification to passenger {}", booking.getPassengerId());
                    } catch (Exception e) {
                        log.warn("Failed to send reschedule notification to passenger {}: {}", 
                            booking.getPassengerId(), e.getMessage());
                    }
                    
                    // Send reschedule email to passenger
                    if (passengerEmail != null && !passengerEmail.trim().isEmpty()) {
                        try {
                            emailService.sendRideRescheduleToPassenger(
                                passengerEmail,
                                passengerName,
                                driverName,
                                rideDetails,
                                oldDate,
                                oldTime,
                                request.getRideDate(),
                                request.getRideTime()
                            );
                            log.info("Sent reschedule email to passenger: {}", passengerEmail);
                        } catch (Exception e) {
                            log.error("Failed to send reschedule email to passenger {}: {}", 
                                passengerEmail, e.getMessage(), e);
                        }
                    } else {
                        log.warn("Passenger email not found for booking {}, cannot send reschedule email", booking.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch passenger profile for reschedule: {}", e.getMessage(), e);
                    // Only send notification if we haven't sent it yet (in case profile fetch fails)
                    // But we already sent it above, so don't send again
                }
            }
        }
        
        // Update seats (if changed)
        if (!request.getTotalSeats().equals(ride.getTotalSeats())) {
            int seatDifference = request.getTotalSeats() - ride.getTotalSeats();
            ride.setTotalSeats(request.getTotalSeats());
            ride.setAvailableSeats(ride.getAvailableSeats() + seatDifference);
        }
        
        ride.setNotes(request.getNotes());
        
        // Get driver and vehicle details before updating denormalized data
        // User Service extracts user ID from JWT token automatically
        Map<String, Object> driverProfile;
        List<Map<String, Object>> vehicles;
        try {
            driverProfile = userServiceClient.getUserProfile(authorization);
            vehicles = userServiceClient.getUserVehicles(authorization);
        } catch (Exception e) {
            throw new BadRequestException("Failed to fetch driver/vehicle details from User Service: " + e.getMessage());
        }
        
        // Update denormalized driver and vehicle details
        if (driverProfile != null) {
            ride.setDriverName((String) driverProfile.get("name"));
        }
        
        // Update vehicle ID if changed
        Long currentVehicleId = ride.getVehicleId();
        if (request.getVehicleId() != null && !request.getVehicleId().equals(currentVehicleId)) {
            ride.setVehicleId(request.getVehicleId());
            currentVehicleId = request.getVehicleId();
        }
        
        // Find and update vehicle details
        final Long vehicleIdToFind = currentVehicleId;
        Map<String, Object> vehicle = vehicles.stream()
            .filter(v -> {
                Object idObj = v.get("id");
                if (idObj == null) return false;
                // Handle both Integer and Long types
                Long vId = idObj instanceof Long ? (Long) idObj : 
                           idObj instanceof Integer ? ((Integer) idObj).longValue() : null;
                return vId != null && vId.equals(vehicleIdToFind);
            })
            .findFirst()
            .orElse(null);
        
        // Update denormalized vehicle details
        if (vehicle != null) {
            ride.setVehicleModel((String) vehicle.get("model"));
            ride.setVehicleLicensePlate((String) vehicle.get("licensePlate"));
            ride.setVehicleColor((String) vehicle.get("color"));
            ride.setVehicleCapacity((Integer) vehicle.get("capacity"));
        }
        
        // Store vehicle ID before saving (needed for lambda expression)
        final Long vehicleId = ride.getVehicleId();
        
        // Save the updated ride
        ride = rideRepository.save(ride);
        
        log.info("Ride updated successfully: rideId={}, rescheduled={}", ride.getId(), isReschedule);
        
        return buildRideResponse(ride, driverProfile, vehicle);
    }
    
    /**
     * Cancel a ride (Driver only)
     * @param rideId Ride ID
     * @param driverId Driver's user ID
     * @return Cancelled RideResponse
     */
    public RideResponse cancelRide(Long rideId, Long driverId) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can cancel the ride");
        }
        
        // Validate ride can be cancelled
        if (ride.getStatus() == RideStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed ride");
        }
        
        if (ride.getStatus() == RideStatus.CANCELLED) {
            throw new BadRequestException("Ride is already cancelled");
        }
        
        // Cancel all active bookings
        List<Booking> activeBookings = bookingRepository.findByRide(ride).stream()
            .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
            .collect(Collectors.toList());
        
        // Get driver name for notifications
        String driverName = ride.getDriverName() != null ? ride.getDriverName() : "Driver";
        
        // Prepare ride details for email
        Map<String, Object> rideDetails = new HashMap<>();
        rideDetails.put("source", ride.getSource());
        rideDetails.put("destination", ride.getDestination());
        rideDetails.put("rideDate", ride.getRideDate());
        rideDetails.put("rideTime", ride.getRideTime());
        rideDetails.put("vehicleModel", ride.getVehicleModel());
        rideDetails.put("vehicleLicensePlate", ride.getVehicleLicensePlate());
        
        for (Booking booking : activeBookings) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            
            // Get passenger information for email
            try {
                Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
                String passengerEmail = passengerProfile != null && passengerProfile.get("email") != null 
                    ? (String) passengerProfile.get("email") : null;
                String passengerName = passengerProfile != null && passengerProfile.get("name") != null 
                    ? (String) passengerProfile.get("name") : "Passenger";
                
                // Send real-time notification to passenger about ride cancellation
                try {
                    notificationService.notifyPassengerRideCancelled(
                        booking.getPassengerId(),
                        ride.getId(),
                        driverName,
                        "Driver cancelled the ride"
                    );
                    log.info("Sent real-time cancellation notification to passenger {}", booking.getPassengerId());
                } catch (Exception e) {
                    log.warn("Failed to send cancellation notification to passenger {}: {}", 
                        booking.getPassengerId(), e.getMessage());
                }
                
                // Send cancellation email to passenger
                if (passengerEmail != null && !passengerEmail.trim().isEmpty()) {
                    try {
                        emailService.sendRideCancellationToPassenger(
                            passengerEmail,
                            passengerName,
                            driverName,
                            rideDetails,
                            "Driver cancelled the ride"
                        );
                        log.info("Sent cancellation email to passenger: {}", passengerEmail);
                    } catch (Exception e) {
                        log.error("Failed to send cancellation email to passenger {}: {}", 
                            passengerEmail, e.getMessage(), e);
                    }
                } else {
                    log.warn("Passenger email not found for booking {}, cannot send cancellation email", booking.getId());
                }
            } catch (Exception e) {
                log.error("Failed to fetch passenger profile for cancellation email: {}", e.getMessage(), e);
                // Still send real-time notification even if email fails
                try {
                    notificationService.notifyPassengerRideCancelled(
                        booking.getPassengerId(),
                        ride.getId(),
                        driverName,
                        "Driver cancelled the ride"
                    );
                } catch (Exception notifEx) {
                    log.warn("Failed to send cancellation notification to passenger {}: {}", 
                        booking.getPassengerId(), notifEx.getMessage());
                }
            }
        }
        
        // Update ride status
        ride.setStatus(RideStatus.CANCELLED);
        ride = rideRepository.save(ride);
        
        // Use buildRideResponseWithDetails to show denormalized data if available
        return buildRideResponseWithDetails(ride, null);
    }
    
    /**
     * Get all rides posted by a driver
     * @param driverId Driver's user ID
     * @param authorization Optional authorization token for backfilling denormalized data
     * @return List of rides posted by the driver
     */
    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(Long driverId, String authorization) {
        List<Ride> rides = rideRepository.findByDriverId(driverId);
        return rides.stream()
            .map(ride -> buildRideResponseWithDetails(ride, authorization))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all bookings made by a passenger
     * @param passengerId Passenger's user ID
     * @return List of bookings
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long passengerId) {
        List<Booking> bookings = bookingRepository.findByPassengerId(passengerId);
        return bookings.stream()
            .map(booking -> buildBookingResponse(booking, null, booking.getRide()))
            .collect(Collectors.toList());
    }
    
    /**
     * Update ride status
     * @param rideId Ride ID
     * @param driverId Driver's user ID
     * @param status New status
     * @return Updated RideResponse
     */
    public RideResponse updateRideStatus(Long rideId, Long driverId, RideStatus status) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can update the ride status");
        }
        
        // Validate status transition
        if (ride.getStatus() == RideStatus.CANCELLED && status != RideStatus.CANCELLED) {
            throw new BadRequestException("Cannot change status of a cancelled ride");
        }
        
        if (ride.getStatus() == RideStatus.COMPLETED && status != RideStatus.COMPLETED) {
            throw new BadRequestException("Cannot change status of a completed ride");
        }
        
        // Time-based safety check: Can't complete before ride date/time
        if (status == RideStatus.COMPLETED) {
            LocalDate rideDate = ride.getRideDate();
            LocalTime rideTime = ride.getRideTime();
            if (rideDate != null && rideTime != null) {
                LocalDateTime rideDateTime = LocalDateTime.of(rideDate, rideTime);
                if (LocalDateTime.now().isBefore(rideDateTime)) {
                    throw new BadRequestException(
                        String.format("Cannot mark ride as completed before scheduled date/time. " +
                            "Scheduled: %s, Current: %s", rideDateTime, LocalDateTime.now())
                    );
                }
            }
        }
        
        ride.setStatus(status);
        ride = rideRepository.save(ride);
        
        // If ride is marked as COMPLETED, just mark driver as confirmed (DO NOT send OTP automatically)
        // Driver will manually send OTP to each passenger via "Send OTP" button
        if (status == RideStatus.COMPLETED) {
            try {
                // Get all confirmed bookings for this ride
                List<Booking> confirmedBookings = bookingRepository.findByRideIdAndStatus(
                    rideId, BookingStatus.CONFIRMED);
                
                log.info("Ride marked as COMPLETED: rideId={}, found {} confirmed bookings", 
                    rideId, confirmedBookings.size());
                
                // Mark driver as confirmed for all bookings (but don't generate OTP yet)
                for (Booking booking : confirmedBookings) {
                    if (booking.getPaymentId() != null && (booking.getDriverConfirmed() == null || !booking.getDriverConfirmed())) {
                        booking.setDriverConfirmed(true);
                        booking.setDriverConfirmedAt(LocalDateTime.now());
                            bookingRepository.save(booking);
                        log.info("Driver confirmed completion for booking: bookingId={}. OTP will be sent when driver clicks 'Send OTP' button.", 
                            booking.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing driver confirmation for completed ride: rideId={}, error={}", 
                    rideId, e.getMessage(), e);
                // Don't fail the ride status update if confirmation fails
            }
        }
        
        // Use buildRideResponseWithDetails to show denormalized data if available
        return buildRideResponseWithDetails(ride, null);
    }
    
    /**
     * Build RideResponse with driver and vehicle details
     * Uses denormalized data stored in Ride entity, or fetches from User Service if needed
     * @param ride Ride entity (contains denormalized driver/vehicle info)
     * @param authorization Optional JWT token for fetching additional details
     * @return RideResponse with details
     */
    private RideResponse buildRideResponseWithDetails(Ride ride, String authorization) {
        // Use denormalized data from Ride entity
        Map<String, Object> driverProfile = null;
        Map<String, Object> vehicle = null;
        
        // Check if denormalized data is missing (for rides created before denormalization)
        boolean needsBackfill = (ride.getDriverName() == null || ride.getVehicleModel() == null);
        
        // Build driver profile from denormalized data
        if (ride.getDriverName() != null) {
            driverProfile = new HashMap<>();
            driverProfile.put("name", ride.getDriverName());
            // Email not stored, would need to fetch if needed
        }
        
        // Build vehicle info from denormalized data
        if (ride.getVehicleModel() != null) {
            vehicle = new HashMap<>();
            vehicle.put("model", ride.getVehicleModel());
            vehicle.put("licensePlate", ride.getVehicleLicensePlate());
            vehicle.put("color", ride.getVehicleColor());
            vehicle.put("capacity", ride.getVehicleCapacity());
            vehicle.put("id", ride.getVehicleId());
        }
        
        // If denormalized data is missing, try to fetch from User Service using public endpoints
        // This handles existing rides created before denormalization was added
        if (needsBackfill) {
            try {
                // Fetch driver information using public endpoint (no auth required)
                if (ride.getDriverName() == null && ride.getDriverId() != null) {
                    try {
                        Map<String, Object> fetchedDriverInfo = userServiceClient.getUserPublicInfo(ride.getDriverId());
                        if (fetchedDriverInfo != null) {
                            driverProfile = fetchedDriverInfo;
                            // Update denormalized data in database
                            if (ride.getDriverName() == null && fetchedDriverInfo.get("name") != null) {
                                ride.setDriverName((String) fetchedDriverInfo.get("name"));
                            }
                        }
                    } catch (Exception e) {
                        // Failed to fetch driver info, continue
                    }
                }
                
                // Fetch vehicle information using public endpoint (no auth required)
                if (ride.getVehicleModel() == null && ride.getVehicleId() != null) {
                    try {
                        Map<String, Object> fetchedVehicleInfo = userServiceClient.getVehiclePublicInfo(ride.getVehicleId());
                        if (fetchedVehicleInfo != null) {
                            vehicle = fetchedVehicleInfo;
                            // Update denormalized data in database
                            if (ride.getVehicleModel() == null) {
                                ride.setVehicleModel((String) fetchedVehicleInfo.get("model"));
                                ride.setVehicleLicensePlate((String) fetchedVehicleInfo.get("licensePlate"));
                                ride.setVehicleColor((String) fetchedVehicleInfo.get("color"));
                                Object capacityObj = fetchedVehicleInfo.get("capacity");
                                if (capacityObj != null) {
                                    ride.setVehicleCapacity(capacityObj instanceof Integer ? (Integer) capacityObj : 
                                                          capacityObj instanceof Long ? ((Long) capacityObj).intValue() : null);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Failed to fetch vehicle info, continue
                    }
                }
                
                // Save updated denormalized data if any was fetched
                // Use separate transaction to ensure save happens even if main transaction is read-only
                if (ride.getDriverName() != null || ride.getVehicleModel() != null) {
                    saveDenormalizedData(ride);
                }
            } catch (Exception e) {
                // Failed to fetch, continue with existing data (or null)
            }
        }
        
        return buildRideResponse(ride, driverProfile, vehicle);
    }
    
    /**
     * Save denormalized data to database
     * Uses REQUIRES_NEW propagation to ensure save happens even if called from read-only transaction
     * @param ride Ride entity with updated denormalized data
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveDenormalizedData(Ride ride) {
        rideRepository.save(ride);
    }
    
    /**
     * Backfill denormalized data for existing rides
     * Updates the ride entity with driver and vehicle details if missing
     * @param ride Ride entity to backfill
     * @param driverProfile Driver profile data
     * @param vehicle Vehicle data
     */
    private void backfillDenormalizedData(Ride ride, Map<String, Object> driverProfile, Map<String, Object> vehicle) {
        boolean needsUpdate = false;
        
        // Update driver name if missing
        if (ride.getDriverName() == null && driverProfile != null && driverProfile.get("name") != null) {
            ride.setDriverName((String) driverProfile.get("name"));
            needsUpdate = true;
        }
        
        // Update vehicle details if missing
        if (ride.getVehicleModel() == null && vehicle != null) {
            ride.setVehicleModel((String) vehicle.get("model"));
            ride.setVehicleLicensePlate((String) vehicle.get("licensePlate"));
            ride.setVehicleColor((String) vehicle.get("color"));
            ride.setVehicleCapacity((Integer) vehicle.get("capacity"));
            needsUpdate = true;
        }
        
        // Save if any updates were made
        if (needsUpdate) {
            rideRepository.save(ride);
        }
    }
    
    /**
     * Build RideResponse from Ride entity with optional driver and vehicle details
     */
    private RideResponse buildRideResponse(Ride ride, Map<String, Object> driverProfile, Map<String, Object> vehicle) {
        RideResponse response = new RideResponse();
        response.setId(ride.getId());
        response.setDriverId(ride.getDriverId());
        response.setVehicleId(ride.getVehicleId());
        response.setSource(ride.getSource());
        response.setDestination(ride.getDestination());
        response.setRideDate(ride.getRideDate());
        response.setRideTime(ride.getRideTime());
        response.setTotalSeats(ride.getTotalSeats());
        response.setAvailableSeats(ride.getAvailableSeats());
        response.setStatus(ride.getStatus());
        // Fare-related fields
        response.setDistanceKm(ride.getDistanceKm());
        response.setTotalFare(ride.getTotalFare());
        response.setBaseFare(ride.getBaseFare());
        response.setRatePerKm(ride.getRatePerKm());
        response.setCurrency(ride.getCurrency());
        response.setNotes(ride.getNotes());
        response.setCreatedAt(ride.getCreatedAt());
        response.setUpdatedAt(ride.getUpdatedAt());
        
        // Add driver info if available
        if (driverProfile != null) {
            response.setDriverName((String) driverProfile.get("name"));
            response.setDriverEmail((String) driverProfile.get("email"));
        }
        
        // Fetch and add driver rating (reviews received as driver)
        if (ride.getDriverId() != null) {
            try {
                UserRatingResponse driverRating = reviewService.getUserRating(ride.getDriverId());
                if (driverRating != null) {
                    // Use driver-specific rating (reviews received as driver)
                    Double driverAvgRating = driverRating.getDriverAverageRating();
                    Long driverReviewsCount = driverRating.getDriverReviews();
                    
                    log.debug("Driver rating fetched for driver {}: averageRating={}, totalReviews={}", 
                            ride.getDriverId(), driverAvgRating, driverReviewsCount);
                    
                    if (driverAvgRating != null && driverAvgRating > 0) {
                        response.setDriverRating(driverAvgRating);
                        response.setDriverTotalReviews(driverReviewsCount != null ? driverReviewsCount : 0L);
                        log.debug("✅ Set driver rating in response: rating={}, reviews={}", 
                                driverAvgRating, driverReviewsCount);
                    } else {
                        log.debug("Driver {} has no driver ratings yet (driverAvgRating={}, driverReviews={})", 
                                ride.getDriverId(), driverAvgRating, driverReviewsCount);
                    }
                } else {
                    log.warn("getUserRating returned null for driver {}", ride.getDriverId());
                }
            } catch (Exception e) {
                log.error("❌ Could not fetch driver rating for driver {}: {}", 
                        ride.getDriverId(), e.getMessage(), e);
                // Rating is optional, continue without it
            }
        }
        
        // Add vehicle info if available
        if (vehicle != null) {
            response.setVehicleModel((String) vehicle.get("model"));
            response.setVehicleLicensePlate((String) vehicle.get("licensePlate"));
            response.setVehicleColor((String) vehicle.get("color"));
            response.setVehicleCapacity((Integer) vehicle.get("capacity"));
        }
        
        return response;
    }
    
    /**
     * Normalize location name for matching (remove common suffixes, lowercase, trim)
     */
    private String normalizeLocationName(String location) {
        if (location == null) return "";
        String normalized = location.toLowerCase().trim();
        // Remove common suffixes that might differ between search and stored values
        normalized = normalized.replaceAll(",\\s*andhra pradesh", "");
        normalized = normalized.replaceAll(",\\s*ap", "");
        normalized = normalized.replaceAll(",\\s*india", "");
        normalized = normalized.replaceAll(",\\s*in", "");
        return normalized.trim();
    }
    
    /**
     * Extract core location name (first part before comma, or full name if no comma)
     */
    private String extractCoreLocationName(String location) {
        if (location == null || location.isEmpty()) return "";
        String normalized = normalizeLocationName(location);
        // Get the first part before comma (main location name)
        int commaIndex = normalized.indexOf(',');
        if (commaIndex > 0) {
            return normalized.substring(0, commaIndex).trim();
        }
        return normalized;
    }
    
    /**
     * Build BookingResponse from Booking entity
     */
    private BookingResponse buildBookingResponse(Booking booking, Map<String, Object> passengerProfile, Ride ride) {
        BookingResponse response = new BookingResponse();
        response.setId(booking.getId());
        response.setRideId(booking.getRide().getId());
        response.setPassengerId(booking.getPassengerId());
        response.setSeatsBooked(booking.getSeatsBooked());
        response.setStatus(booking.getStatus());
        response.setCreatedAt(booking.getCreatedAt());
        response.setUpdatedAt(booking.getUpdatedAt());
        response.setPassengerSource(booking.getPassengerSource());
        response.setPassengerDestination(booking.getPassengerDestination());
        response.setPassengerDistanceKm(booking.getPassengerDistanceKm());
        
        // CRITICAL: Ensure passengerFare is correctly set (should already be total for all seats)
        Double passengerFare = booking.getPassengerFare();
        Integer seatsBooked = booking.getSeatsBooked();
        
        // CRITICAL: Safety check - if passengerFare seems too low for multiple seats, it might be per-seat fare
        // This can happen if old bookings exist or if there was a bug in previous code
        if (passengerFare != null && seatsBooked != null && seatsBooked > 1) {
            // Rough heuristic: if fare is less than 200 for 2+ seats, it's likely per-seat fare
            // This is a safety check - normally passengerFare should already be multiplied
            if (passengerFare < 200) {
                log.warn("⚠️ WARNING: Booking {} has passengerFare {} {} for {} seats - this seems like per-seat fare, not total!", 
                    booking.getId(), passengerFare, booking.getCurrency(), seatsBooked);
                log.warn("⚠️ Expected fare should be at least {} {} (assuming 100 per seat minimum)", 
                    seatsBooked * 100, booking.getCurrency());
                // DO NOT modify the database value - data integrity is important
                // But log this so we can identify the issue
            } else {
                log.debug("📋 Building booking response - ID: {}, Seats: {}, PassengerFare: {} {} (looks correct)", 
                    booking.getId(), seatsBooked, passengerFare, booking.getCurrency());
            }
        }
        
        response.setPassengerFare(passengerFare);
        response.setCurrency(booking.getCurrency());
        
        // Add payment ID if available
        response.setPaymentId(booking.getPaymentId());
        
        // Add OTP verification fields
        response.setDriverConfirmed(booking.getDriverConfirmed());
        response.setPassengerConfirmed(booking.getPassengerConfirmed());
        response.setHasOtp(booking.getOtp() != null && !booking.getOtp().trim().isEmpty());
        // Don't send actual OTP to frontend for security
        
        // Add passenger info if available
        if (passengerProfile != null) {
            response.setPassengerName((String) passengerProfile.get("name"));
            response.setPassengerEmail((String) passengerProfile.get("email"));
        }
        
        // Add ride details if available
        if (ride != null) {
            response.setRideDetails(buildRideResponseWithDetails(ride, null));
        }
        
        return response;
    }
    
    /**
     * Verify OTP and complete ride (credit wallet)
     * @param bookingId Booking ID
     * @param driverId Driver's user ID
     * @param otp OTP provided by driver (received from passenger)
     * @return Response with completion status
     */
    public Map<String, Object> verifyOtpAndCompleteRide(Long bookingId, Long driverId, String otp) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        
        Ride ride = booking.getRide();
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can verify OTP");
        }
        
        // Validate booking is in correct state
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Booking must be CONFIRMED to verify OTP");
        }
        
        // Validate driver has confirmed
        if (booking.getDriverConfirmed() == null || !booking.getDriverConfirmed()) {
            throw new BadRequestException("Driver must mark ride as completed first");
        }
        
        // Validate OTP exists
        if (booking.getOtp() == null || booking.getOtp().trim().isEmpty()) {
            throw new BadRequestException("No OTP found for this booking. Please mark ride as completed first.");
        }
        
        // Validate OTP
        boolean isValid = otpService.validateOtp(otp, booking.getOtp(), booking.getOtpExpiresAt());
        
        if (!isValid) {
            throw new BadRequestException("Invalid or expired OTP. Please check the OTP and try again.");
        }
        
        // OTP is valid - mark passenger as confirmed
        booking.setPassengerConfirmed(true);
        booking.setPassengerConfirmedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        
        log.info("OTP verified successfully for booking: bookingId={}, driverId={}", bookingId, driverId);
        
        // Credit driver wallet if payment exists
        if (booking.getPaymentId() != null) {
            try {
                paymentServiceClient.creditDriverWallet(booking.getPaymentId());
                log.info("Credited driver wallet for booking: bookingId={}, paymentId={}", 
                    booking.getId(), booking.getPaymentId());
                
                // Update booking status to COMPLETED
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "OTP verified successfully. Wallet credited.");
                response.put("bookingId", booking.getId());
                response.put("status", "COMPLETED");
                return response;
                
            } catch (Exception e) {
                log.error("Failed to credit driver wallet for booking: bookingId={}, paymentId={}, error={}", 
                    booking.getId(), booking.getPaymentId(), e.getMessage(), e);
                throw new BadRequestException("OTP verified but failed to credit wallet: " + e.getMessage());
            }
        } else {
            log.warn("Booking has no paymentId, cannot credit wallet: bookingId={}", booking.getId());
            throw new BadRequestException("Booking has no payment associated. Cannot credit wallet.");
        }
    }
    
    /**
     * Get bookings for a ride (for driver to see which bookings need OTP verification)
     * @param rideId Ride ID
     * @param driverId Driver's user ID
     * @return List of bookings for the ride
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getRideBookings(Long rideId, Long driverId) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can view bookings");
        }
        
        List<Booking> bookings = bookingRepository.findByRideId(rideId);
        
        return bookings.stream()
            .map(booking -> {
                try {
                    Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
                    return buildBookingResponse(booking, passengerProfile, ride);
                } catch (Exception e) {
                    log.warn("Failed to fetch passenger profile for booking: bookingId={}, error={}", 
                        booking.getId(), e.getMessage());
                    return buildBookingResponse(booking, null, ride);
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Send OTP to a specific passenger for ride completion verification
     * Called when driver clicks "Send OTP to Passenger X" button
     * @param bookingId Booking ID
     * @param driverId Driver's user ID
     * @return Response with OTP sending status
     */
    public Map<String, Object> sendOtpToPassenger(Long bookingId, Long driverId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        
        Ride ride = booking.getRide();
        
        // Validate driver owns the ride
        if (!ride.getDriverId().equals(driverId)) {
            throw new BadRequestException("Only the ride owner can send OTP");
        }
        
        // Validate booking is in correct state
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Booking must be CONFIRMED to send OTP");
        }
        
        // Validate ride is marked as COMPLETED
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Ride must be marked as COMPLETED before sending OTP");
        }
        
        // Validate driver has confirmed (ride must be marked as COMPLETED)
        if (booking.getDriverConfirmed() == null || !booking.getDriverConfirmed()) {
            throw new BadRequestException("Ride must be marked as COMPLETED first before sending OTP");
        }
        
        // Validate payment exists
        if (booking.getPaymentId() == null) {
            throw new BadRequestException("Booking has no payment associated. Cannot send OTP.");
        }
        
        // Generate new OTP (or regenerate if expired)
        String otp = otpService.generateOtp();
        LocalDateTime otpExpiresAt = otpService.getOtpExpirationTime();
        
        booking.setOtp(otp);
        booking.setOtpExpiresAt(otpExpiresAt);
        bookingRepository.save(booking);
        
        log.info("Generated OTP for booking: bookingId={}, otp={}, expiresAt={}", 
            booking.getId(), otp, otpExpiresAt);
        
        // Get passenger email from User Service
        try {
            Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
            String passengerEmail = passengerProfile != null && passengerProfile.get("email") != null 
                ? (String) passengerProfile.get("email") : null;
            String passengerName = passengerProfile != null && passengerProfile.get("name") != null 
                ? (String) passengerProfile.get("name") : "Passenger";
            
            // Send real-time notification to passenger about OTP
            try {
                String driverName = ride.getDriverName() != null ? ride.getDriverName() : "Driver";
                notificationService.notifyPassengerRideCompleted(
                    booking.getPassengerId(),
                    booking.getId(),
                    driverName
                );
            } catch (Exception e) {
                log.warn("Failed to send real-time notification to passenger: {}", e.getMessage());
            }
            
            if (passengerEmail == null || passengerEmail.trim().isEmpty()) {
                log.error("Passenger email not found for booking: bookingId={}, passengerId={}", 
                    booking.getId(), booking.getPassengerId());
                throw new BadRequestException("Passenger email not found. Cannot send OTP.");
            }
            
            // Send OTP email to passenger
            try {
                emailService.sendRideCompletionOtp(
                    passengerEmail,
                    passengerName,
                    ride.getSource(),
                    ride.getDestination(),
                    ride.getRideDate(),
                    ride.getRideTime(),
                    otp
                );
                
                log.info("✅ OTP email sending initiated for passenger: bookingId={}, email={}, otp={}", 
                    booking.getId(), passengerEmail, otp);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "OTP sent successfully to passenger's email");
                response.put("bookingId", booking.getId());
                response.put("passengerEmail", passengerEmail);
                response.put("passengerName", passengerName);
                return response;
                
            } catch (Exception emailEx) {
                log.error("❌ Failed to send OTP email for booking: bookingId={}, email={}, error={}", 
                    booking.getId(), passengerEmail, emailEx.getMessage(), emailEx);
                // Throw exception so frontend knows email sending failed
                // OTP is still generated and stored, driver can retry
                throw new BadRequestException("Failed to send OTP email: " + emailEx.getMessage() + 
                    ". OTP has been generated. Please try resending.");
            }
            
        } catch (BadRequestException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Failed to process OTP sending for booking: bookingId={}, error={}", 
                booking.getId(), e.getMessage(), e);
            throw new BadRequestException("Failed to send OTP: " + e.getMessage());
        }
    }
}

