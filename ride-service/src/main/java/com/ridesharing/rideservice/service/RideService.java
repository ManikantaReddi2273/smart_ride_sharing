
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
    private ObjectMapper objectMapper;
    
    @Autowired
    private RouteGeometryUtil routeGeometryUtil;
    
    @Value("${fare.base-fare:50.0}")
    private Double baseFare;
    
    @Value("${fare.rate-per-km:10.0}")
    private Double ratePerKm;
    
    @Value("${fare.currency:INR}")
    private String currency;
    
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
        
        // Store route geometry for partial route matching
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
            }
        } else {
            log.warn("⚠️ Route geometry not available for ride - partial route matching will be disabled");
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
        
        log.info("🔍 SEARCH RIDES - Found {} total rides for date {}", rides.size(), request.getRideDate());
        log.info("   Search request - Source: '{}', Destination: '{}'", request.getSource(), request.getDestination());
        
        // CRITICAL: Geocode passenger source/destination if coordinates not provided
        // This enables partial route matching even when user types manually (not using autocomplete)
        Double passengerSourceLat = request.getSourceLatitude();
        Double passengerSourceLon = request.getSourceLongitude();
        Double passengerDestLat = request.getDestinationLatitude();
        Double passengerDestLon = request.getDestinationLongitude();
        
        if (passengerSourceLat == null || passengerSourceLon == null) {
            try {
                log.info("🔍 Geocoding passenger source: '{}' (coordinates not provided)", request.getSource());
                String[] sourceCoords = googleMapsService.geocodeAddress(request.getSource());
                if (sourceCoords != null && sourceCoords.length >= 2) {
                    passengerSourceLon = Double.parseDouble(sourceCoords[0]);
                    passengerSourceLat = Double.parseDouble(sourceCoords[1]);
                    log.info("✅ Geocoded passenger source: [lat={}, lon={}]", passengerSourceLat, passengerSourceLon);
                } else {
                    log.warn("⚠️ Failed to geocode passenger source: '{}'", request.getSource());
                }
            } catch (Exception ex) {
                log.warn("⚠️ Error geocoding passenger source '{}': {}", request.getSource(), ex.getMessage());
            }
        }
        
        if (passengerDestLat == null || passengerDestLon == null) {
            try {
                log.info("🔍 Geocoding passenger destination: '{}' (coordinates not provided)", request.getDestination());
                String[] destCoords = googleMapsService.geocodeAddress(request.getDestination());
                if (destCoords != null && destCoords.length >= 2) {
                    passengerDestLon = Double.parseDouble(destCoords[0]);
                    passengerDestLat = Double.parseDouble(destCoords[1]);
                    log.info("✅ Geocoded passenger destination: [lat={}, lon={}]", passengerDestLat, passengerDestLon);
                } else {
                    log.warn("⚠️ Failed to geocode passenger destination: '{}'", request.getDestination());
                }
            } catch (Exception ex) {
                log.warn("⚠️ Error geocoding passenger destination '{}': {}", request.getDestination(), ex.getMessage());
            }
        }
        
        // Prepare passenger coordinates for matching (if available)
        final Double finalSourceLat = passengerSourceLat;
        final Double finalSourceLon = passengerSourceLon;
        final Double finalDestLat = passengerDestLat;
        final Double finalDestLon = passengerDestLon;
        
        // Filter rides based on source and destination matching
        rides = rides.stream()
            .filter(ride -> {
                // ALWAYS include exact text matches (same source and destination)
                // Use case-insensitive partial matching for flexibility
                String searchSource = normalizeLocationName(request.getSource());
                String searchDest = normalizeLocationName(request.getDestination());
                String rideSource = normalizeLocationName(ride.getSource());
                String rideDest = normalizeLocationName(ride.getDestination());
                
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
                
                if (exactTextMatch) {
                    log.info("✅ Exact text match found for ride {}: '{}' -> '{}' (search: '{}' -> '{}')", 
                        ride.getId(), ride.getSource(), ride.getDestination(), 
                        request.getSource(), request.getDestination());
                    return true;
                }
                
                // CRITICAL: Check for partial route matches using coordinates (geocoded if needed)
                if (finalSourceLat != null && finalSourceLon != null &&
                    finalDestLat != null && finalDestLon != null) {
                    
                    log.info("🔍 Checking coordinate-based matching for ride {}: {} -> {} (passenger: {} -> {})", 
                        ride.getId(), ride.getSource(), ride.getDestination(),
                        request.getSource(), request.getDestination());
                    log.info("   Passenger coordinates - Source: [lat={}, lon={}], Destination: [lat={}, lon={}]", 
                        finalSourceLat, finalSourceLon, finalDestLat, finalDestLon);
                    
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
                        log.info("   ⚠️ Ride {} has NO stored geometry (null or empty), using coordinate-based fallback", ride.getId());
                    }
                    
                    // PRIORITY 2: Fallback to coordinate-based matching if geometry not available
                    log.info("   Checking coordinate-based fallback matching for ride {}", ride.getId());
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
                    log.info("⚠️ No coordinates available (geocoding failed) - only text matching used for ride {}: {} -> {}", 
                        ride.getId(), ride.getSource(), ride.getDestination());
                    log.info("   Source coords: lat={}, lon={}, Dest coords: lat={}, lon={}", 
                        finalSourceLat, finalSourceLon, finalDestLat, finalDestLon);
                }
                
                return false;
            })
            .collect(Collectors.toList());
        
        log.info("Found {} rides matching search criteria (exact + partial matches)", rides.size());
        
        // Convert to response DTOs with driver and vehicle details
        List<RideResponse> results = rides.stream()
            .map(ride -> buildRideResponseWithDetails(ride, authorization))
            .collect(Collectors.toList());
        
        // Apply filters
        return results.stream()
            .filter(ride -> {
                // Price filter
                if (request.getMinPrice() != null && ride.getTotalFare() != null) {
                    if (ride.getTotalFare() < request.getMinPrice()) {
                        return false;
                    }
                }
                if (request.getMaxPrice() != null && ride.getTotalFare() != null) {
                    if (ride.getTotalFare() > request.getMaxPrice()) {
                        return false;
                    }
                }
                
                // Vehicle type filter (partial match on model)
                if (request.getVehicleType() != null && !request.getVehicleType().trim().isEmpty()) {
                    String vehicleModel = ride.getVehicleModel();
                    if (vehicleModel == null || 
                        !vehicleModel.toLowerCase().contains(request.getVehicleType().toLowerCase().trim())) {
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
            // 30km allows for reasonable detours, nearby locations, and route variations
            // Increased to ensure exact matches and nearby locations are included
            double maxDistanceFromRouteKm = 30.0;
            
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
                log.info("Passenger taking full route, using stored ride fare: {} {}", ride.getTotalFare(), ride.getCurrency());
                passengerFareResponse = new FareCalculationResponse();
                passengerFareResponse.setDistanceKm(ride.getDistanceKm());
                passengerFareResponse.setBaseFare(ride.getBaseFare() != null ? ride.getBaseFare() : 50.0);
                passengerFareResponse.setRatePerKm(ride.getRatePerKm() != null ? ride.getRatePerKm() : 10.0);
                passengerFareResponse.setTotalFare(ride.getTotalFare());
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
        booking.setPassengerFare(passengerFareResponse.getTotalFare());
        booking.setCurrency(passengerFareResponse.getCurrency());
        
        booking = bookingRepository.save(booking);
        
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
            paymentRequest.put("amount", passengerFareResponse.getTotalFare());
            paymentRequest.put("fare", passengerFareResponse.getTotalFare());
            paymentRequest.put("currency", passengerFareResponse.getCurrency() != null ? passengerFareResponse.getCurrency() : "INR");
            
            log.info("🔔 Initiating payment for bookingId={}, amount={} {}", 
                booking.getId(), passengerFareResponse.getTotalFare(), passengerFareResponse.getCurrency());
            
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
        
        // CRITICAL: Payment order MUST be added to response for frontend to open payment dialog
        // paymentOrderResponse is already validated and normalized above
        if (paymentOrderResponse != null && paymentId != null) {
            bookingResponse.setPaymentId(paymentId);
            bookingResponse.setPaymentOrder(paymentOrderResponse); // Already validated and normalized
            
            log.info("✅ Payment order added to booking response: paymentId={}, orderId={}, keyId={}, amount={}, currency={}", 
                paymentId, 
                paymentOrderResponse.get("orderId"),
                paymentOrderResponse.get("keyId"),
                paymentOrderResponse.get("amount"),
                paymentOrderResponse.get("currency"));
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
        
        // Send confirmation emails after payment verification
        try {
            // Get passenger profile
            Map<String, Object> passengerProfile = userServiceClient.getUserPublicInfo(booking.getPassengerId());
            
            // Get driver profile
            Map<String, Object> driverProfile = userServiceClient.getUserPublicInfo(ride.getDriverId());
            
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
            String driverName = driverProfile.get("name") != null && !((String) driverProfile.get("name")).isEmpty() ? 
                (String) driverProfile.get("name") : ride.getDriverName() != null ? ride.getDriverName() : "Driver";
            String driverEmail = driverProfile.get("email") != null ? 
                ((String) driverProfile.get("email")).trim() : null;
            if (driverEmail != null && driverEmail.isEmpty()) {
                driverEmail = null;
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
        
        // Check if there are confirmed bookings
        long confirmedBookings = bookingRepository.countByRideAndStatusIn(
            ride, 
            Arrays.asList(BookingStatus.CONFIRMED, BookingStatus.PENDING)
        );
        
        if (confirmedBookings > 0) {
            throw new BadRequestException("Cannot update ride with active bookings");
        }
        
        // Update ride details
        ride.setSource(request.getSource());
        ride.setDestination(request.getDestination());
        ride.setRideDate(request.getRideDate());
        ride.setRideTime(request.getRideTime());
        
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
        
        for (Booking booking : activeBookings) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
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
        
        ride.setStatus(status);
        ride = rideRepository.save(ride);
        
        // If ride is marked as COMPLETED, credit driver wallet for all confirmed bookings
        if (status == RideStatus.COMPLETED) {
            try {
                // Get all confirmed bookings for this ride
                List<Booking> confirmedBookings = bookingRepository.findByRideIdAndStatus(
                    rideId, BookingStatus.CONFIRMED);
                
                log.info("Ride marked as COMPLETED: rideId={}, found {} confirmed bookings", 
                    rideId, confirmedBookings.size());
                
                // Credit driver wallet for each confirmed booking with successful payment
                for (Booking booking : confirmedBookings) {
                    if (booking.getPaymentId() != null) {
                        try {
                            paymentServiceClient.creditDriverWallet(booking.getPaymentId());
                            log.info("Credited driver wallet for booking: bookingId={}, paymentId={}", 
                                booking.getId(), booking.getPaymentId());
                            
                            // Update booking status to COMPLETED
                            booking.setStatus(BookingStatus.COMPLETED);
                            bookingRepository.save(booking);
                        } catch (Exception e) {
                            log.error("Failed to credit driver wallet for booking: bookingId={}, paymentId={}, error={}", 
                                booking.getId(), booking.getPaymentId(), e.getMessage(), e);
                            // Don't fail the entire operation if one wallet credit fails
                        }
                    } else {
                        log.warn("Booking has no paymentId, skipping wallet credit: bookingId={}", booking.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing wallet credits for completed ride: rideId={}, error={}", 
                    rideId, e.getMessage(), e);
                // Don't fail the ride status update if wallet credit fails
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
        response.setPassengerFare(booking.getPassengerFare());
        response.setCurrency(booking.getCurrency());
        
        // Add payment ID if available
        response.setPaymentId(booking.getPaymentId());
        
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
}

