
package com.ridesharing.rideservice.service;
import com.ridesharing.rideservice.dto.*;
import com.ridesharing.rideservice.entity.Booking;
import com.ridesharing.rideservice.entity.BookingStatus;
import com.ridesharing.rideservice.entity.Ride;
import com.ridesharing.rideservice.entity.RideStatus;
import com.ridesharing.rideservice.exception.BadRequestException;
import com.ridesharing.rideservice.exception.ResourceNotFoundException;
import com.ridesharing.rideservice.feign.UserServiceClient;
import com.ridesharing.rideservice.repository.BookingRepository;
import com.ridesharing.rideservice.repository.RideRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private FareCalculationService fareCalculationService;
    
    @Autowired
    private GoogleMapsService googleMapsService;
    
    @Autowired
    private EmailService emailService;
    
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
        try {
            if (request.getSourceLatitude() != null && request.getSourceLongitude() != null &&
                request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
                // Use coordinates directly - FASTEST & MOST ACCURATE (no geocoding errors)
                log.info("✅ Using coordinates directly from frontend - skipping geocoding");
                fareResponse = fareCalculationService.calculateFareFromCoordinates(
                        request.getSourceLatitude(),
                        request.getSourceLongitude(),
                        request.getDestinationLatitude(),
                        request.getDestinationLongitude(),
                        request.getSource(),
                        request.getDestination()
                );
            } else {
                // Fallback to geocoding (if coordinates not provided)
                log.info("⚠️ Coordinates not provided - using geocoding (may have errors)");
                fareResponse = fareCalculationService.calculateFare(
                        request.getSource(),
                        request.getDestination()
                );
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
        
        // CRITICAL: If coordinates are provided, use intelligent route matching
        // Otherwise, fall back to text-based search
        if (request.getSourceLatitude() != null && request.getSourceLongitude() != null &&
            request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
            log.info("✅ Using intelligent route matching with coordinates");
            // Get all rides for the date (we'll filter by route matching)
            rides = rideRepository.findByRideDateAndStatusInAndAvailableSeatsGreaterThan(
                request.getRideDate(),
                activeStatuses,
                0
            );
            
            // Filter rides where passenger's route lies along driver's journey
            rides = rides.stream()
                .filter(ride -> isPassengerRouteAlongDriverJourney(
                    request.getSourceLatitude(), request.getSourceLongitude(),
                    request.getDestinationLatitude(), request.getDestinationLongitude(),
                    ride.getSource(), ride.getDestination()
                ))
                .collect(Collectors.toList());
            
            log.info("Found {} rides matching intelligent route criteria", rides.size());
        } else {
            log.info("⚠️ Coordinates not provided - using text-based search");
            // Traditional text-based search
            rides = rideRepository.searchRides(
                request.getSource(),
                request.getDestination(),
                request.getRideDate(),
                activeStatuses
            );
        }
        
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
            // 20km allows for reasonable detours, nearby locations, and route variations
            double maxDistanceFromRouteKm = 20.0;
            
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
            boolean exactMatch = distPassengerSourceToDriverSource <= 5.0 &&
                                distPassengerDestToDriverDest <= 5.0;
            if (exactMatch) {
                log.info("   ✅ Exact match detected (passenger route matches driver route)");
                return true;
            }
            
            // Edge case 2: Reverse exact match - passenger route is reverse of driver route
            // (passenger source = driver dest, passenger dest = driver source)
            boolean reverseMatch = distPassengerSourceToDriverDest <= 5.0 &&
                                  distPassengerDestToDriverSource <= 5.0;
            if (reverseMatch) {
                log.info("   ✅ Reverse match detected (passenger route is reverse of driver route)");
                return true;
            }
            
            // Edge case 3: If passenger's journey is very short compared to driver's, be more lenient
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
            passengerFareResponse = fareCalculationService.calculatePassengerFare(
                    ride.getSource(),
                    ride.getDestination(),
                    request.getPassengerSource(),
                    request.getPassengerDestination()
            );
        } catch (Exception ex) {
            throw new BadRequestException("Failed to calculate passenger fare: " + ex.getMessage());
        }

        // Create booking
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassengerId(passengerId);
        booking.setSeatsBooked(request.getSeatsBooked());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPassengerSource(request.getPassengerSource());
        booking.setPassengerDestination(request.getPassengerDestination());
        booking.setPassengerDistanceKm(passengerFareResponse.getDistanceKm());
        booking.setPassengerFare(passengerFareResponse.getTotalFare());
        booking.setCurrency(passengerFareResponse.getCurrency());
        
        booking = bookingRepository.save(booking);
        
        // Update ride available seats
        ride.setAvailableSeats(ride.getAvailableSeats() - request.getSeatsBooked());
        
        // Update ride status if needed
        if (ride.getStatus() == RideStatus.POSTED) {
            ride.setStatus(RideStatus.BOOKED);
        }
        
        rideRepository.save(ride);
        
        // Get passenger profile from logged-in user account
        Map<String, Object> passengerProfile;
        try {
            passengerProfile = userServiceClient.getUserProfile(authorization);
        } catch (Exception e) {
            throw new BadRequestException("Failed to fetch passenger profile from User Service: " + e.getMessage());
        }
        
        // Get driver profile
        Map<String, Object> driverProfile;
        try {
            driverProfile = userServiceClient.getUserPublicInfo(ride.getDriverId());
        } catch (Exception e) {
            log.warn("Failed to fetch driver profile for email: {}", e.getMessage());
            driverProfile = new HashMap<>();
            driverProfile.put("name", ride.getDriverName() != null ? ride.getDriverName() : "Driver");
            driverProfile.put("email", null); // Email not available
        }
        
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
        
        // Log email information for debugging
        log.info("Preparing to send booking emails - Passenger: {} ({}), Driver: {} ({})", 
            passengerName, passengerEmail, driverName, driverEmail);
        
        // Send emails asynchronously
        if (passengerEmail != null && !passengerEmail.isEmpty()) {
            try {
                log.info("Sending booking confirmation email to passenger: {}", passengerEmail);
                emailService.sendBookingConfirmationToPassenger(
                    passengerEmail,
                    passengerName,
                    driverName,
                    driverEmail != null ? driverEmail : "N/A",
                    rideDetails,
                    request.getSeatsBooked()
                );
                log.info("Booking confirmation email queued for passenger: {}", passengerEmail);
            } catch (Exception e) {
                log.error("Error sending email to passenger {}: {}", passengerEmail, e.getMessage(), e);
            }
        } else {
            log.warn("Passenger email is null or empty, cannot send confirmation email. Passenger profile: {}", passengerProfile);
        }
        
        if (driverEmail != null && !driverEmail.isEmpty()) {
            try {
                log.info("Sending booking notification email to driver: {}", driverEmail);
                emailService.sendBookingNotificationToDriver(
                    driverEmail,
                    driverName,
                    passengerName,
                    passengerEmail != null ? passengerEmail : "N/A",
                    passengerPhone,
                    rideDetails,
                    request.getSeatsBooked()
                );
                log.info("Booking notification email queued for driver: {}", driverEmail);
            } catch (Exception e) {
                log.error("Error sending email to driver {}: {}", driverEmail, e.getMessage(), e);
            }
        } else {
            log.warn("Driver email is null or empty, cannot send notification email. Driver profile: {}", driverProfile);
        }
        
        return buildBookingResponse(booking, passengerProfile, null);
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

