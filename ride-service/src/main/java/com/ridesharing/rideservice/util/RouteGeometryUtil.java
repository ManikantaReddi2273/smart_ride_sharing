package com.ridesharing.rideservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Route Geometry Utility
 * Provides spatial calculations for route matching:
 * - Point-to-polyline distance calculation
 * - Finding nearest point on polyline
 * - Route ordering validation
 */
@Component
@Slf4j
public class RouteGeometryUtil {
    
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    // Configurable threshold (default: 50km)
    @Value("${route.matching.max-distance-meters:50000.0}")
    private double maxDistanceMeters;
    
    private final ObjectMapper objectMapper;
    
    public RouteGeometryUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get the configured maximum distance threshold
     */
    public double getMaxDistanceMeters() {
        return maxDistanceMeters;
    }
    
    /**
     * Parse route geometry JSON string to list of coordinate arrays.
     * 
     * @param routeGeometryJson JSON string containing array of [longitude, latitude] coordinates
     * @return List of coordinate arrays [longitude, latitude], or empty list if parsing fails
     */
    public List<double[]> parseRouteGeometry(String routeGeometryJson) {
        List<double[]> coordinates = new ArrayList<>();
        
        if (routeGeometryJson == null || routeGeometryJson.trim().isEmpty()) {
            log.error("❌❌❌ CRITICAL: Route geometry JSON is null or empty");
            return coordinates;
        }
        
        log.debug("🔍 Parsing route geometry JSON (length: {} chars)", routeGeometryJson.length());
        
        try {
            JsonNode root = objectMapper.readTree(routeGeometryJson);
            log.debug("   JSON root type: {}, isArray: {}", root.getNodeType(), root.isArray());
            
            if (root.isArray()) {
                int nodeCount = 0;
                for (JsonNode coordNode : root) {
                    nodeCount++;
                    if (coordNode.isArray() && coordNode.size() >= 2) {
                        // CRITICAL FIX: OpenRouteService Directions API returns coordinates as [lon, lat] (GeoJSON format)
                        // However, we need to verify the actual format. If stored as [lat, lon], swap them.
                        double coord0 = coordNode.get(0).asDouble();
                        double coord1 = coordNode.get(1).asDouble();
                        
                        // Detect format: If first coordinate is in latitude range (-90 to 90) and second is in longitude range (-180 to 180),
                        // it's likely [lat, lon] format and needs swapping
                        // If first coordinate is in longitude range and second is in latitude range, it's [lon, lat] (correct)
                        boolean likelyLatLonFormat = (coord0 >= -90 && coord0 <= 90 && Math.abs(coord1) > 90);
                        boolean likelyLonLatFormat = (Math.abs(coord0) > 90 && coord1 >= -90 && coord1 <= 90);
                        
                        double lon, lat;
                        if (likelyLatLonFormat) {
                            // Incoming format is [lat, lon] - swap to [lon, lat]
                            lat = coord0;
                            lon = coord1;
                            log.debug("   Detected [lat, lon] format, swapped to [lon, lat] for point {}", nodeCount);
                        } else if (likelyLonLatFormat) {
                            // Incoming format is [lon, lat] - correct, use as-is
                            lon = coord0;
                            lat = coord1;
                        } else {
                            // Ambiguous - assume [lon, lat] (GeoJSON standard) but log warning
                            lon = coord0;
                            lat = coord1;
                            if (nodeCount == 1) {
                                log.warn("   ⚠️ Ambiguous coordinate format for first point: [{}, {}]. Assuming [lon, lat]. " +
                                        "If matching fails, coordinates may be swapped.", coord0, coord1);
                            }
                        }
                        
                        // Internally we always use [lon, lat] format
                        coordinates.add(new double[]{lon, lat});
                    } else {
                        log.warn("   ⚠️ Skipping invalid coordinate node {}: isArray={}, size={}", 
                            nodeCount, coordNode.isArray(), coordNode.size());
                    }
                }
                log.info("✅ Parsed {} coordinate points from route geometry (processed {} nodes)", 
                    coordinates.size(), nodeCount);
                
                if (coordinates.size() > 0) {
                    log.debug("   First point: [lon={}, lat={}], Last point: [lon={}, lat={}]", 
                        coordinates.get(0)[0], coordinates.get(0)[1],
                        coordinates.get(coordinates.size() - 1)[0], coordinates.get(coordinates.size() - 1)[1]);
                }
            } else {
                log.error("❌❌❌ CRITICAL: Route geometry JSON is not an array! Type: {}", root.getNodeType());
                log.error("   JSON preview (first 200 chars): {}", 
                    routeGeometryJson.length() > 200 ? routeGeometryJson.substring(0, 200) + "..." : routeGeometryJson);
            }
        } catch (Exception ex) {
            log.error("❌❌❌ CRITICAL: Failed to parse route geometry JSON: {}", ex.getMessage(), ex);
            log.error("   Exception type: {}", ex.getClass().getName());
            log.error("   JSON content (first 500 chars): {}", 
                routeGeometryJson.length() > 500 ? routeGeometryJson.substring(0, 500) + "..." : routeGeometryJson);
            ex.printStackTrace();
        }
        
        if (coordinates.isEmpty()) {
            log.error("❌❌❌ CRITICAL: Parsed route geometry resulted in empty coordinate list!");
            log.error("   This will cause partial matching to fail completely!");
        }
        
        return coordinates;
    }
    
    /**
     * Check if a point is near a polyline (within threshold distance).
     * 
     * @param point Point as [longitude, latitude]
     * @param polyline List of polyline points, each as [longitude, latitude]
     * @param maxDistanceMeters Maximum distance in meters (default: 3000m = 3km)
     * @return true if point is within maxDistanceMeters of any segment in polyline
     */
    public boolean isPointNearPolyline(double[] point, List<double[]> polyline, double maxDistanceMeters) {
        if (point == null || point.length < 2 || polyline == null || polyline.isEmpty()) {
            return false;
        }
        
        double pointLon = point[0];
        double pointLat = point[1];
        
        // Check distance to each segment in the polyline
        for (int i = 0; i < polyline.size() - 1; i++) {
            double[] segmentStart = polyline.get(i);
            double[] segmentEnd = polyline.get(i + 1);
            
            double distanceToSegment = distanceToLineSegment(
                pointLat, pointLon,
                segmentStart[1], segmentStart[0], // [lon, lat] -> (lat, lon)
                segmentEnd[1], segmentEnd[0]
            );
            
            // Convert km to meters
            double distanceMeters = distanceToSegment * 1000.0;
            
            if (distanceMeters <= maxDistanceMeters) {
                log.debug("Point [{}, {}] is {}m from polyline segment (threshold: {}m)", 
                    pointLon, pointLat, String.format("%.2f", distanceMeters), maxDistanceMeters);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get minimum distance from point to polyline (in meters).
     * Helper method for logging and debugging.
     * CRITICAL: This calculates the actual perpendicular distance to the nearest segment.
     */
    private double getMinDistanceToPolyline(double[] point, List<double[]> polyline) {
        if (point == null || point.length < 2 || polyline == null || polyline.isEmpty()) {
            log.warn("⚠️ Invalid input to getMinDistanceToPolyline: point={}, polyline size={}", 
                point != null, polyline != null ? polyline.size() : 0);
            return Double.MAX_VALUE;
        }
        
        double minDistance = Double.MAX_VALUE;
        double pointLon = point[0];
        double pointLat = point[1];
        
        log.debug("   Calculating min distance from point [lon={}, lat={}] to polyline with {} points", 
            pointLon, pointLat, polyline.size());
        
        for (int i = 0; i < polyline.size() - 1; i++) {
            double[] segmentStart = polyline.get(i);
            double[] segmentEnd = polyline.get(i + 1);
            
            // Calculate perpendicular distance to line segment
            double distanceToSegment = distanceToLineSegment(
                pointLat, pointLon,
                segmentStart[1], segmentStart[0], // [lon, lat] -> (lat, lon)
                segmentEnd[1], segmentEnd[0]
            );
            
            double distanceMeters = distanceToSegment * 1000.0;
            if (distanceMeters < minDistance) {
                minDistance = distanceMeters;
                log.debug("   New min distance: {}m (segment {} to {})", 
                    String.format("%.2f", minDistance), i, i + 1);
            }
        }
        
        log.debug("   Final min distance: {}m", String.format("%.2f", minDistance));
        return minDistance;
    }
    
    /**
     * Calculate approximate distance along polyline from start to nearest point to given location.
     * Used for ordering when indices are too close.
     */
    private double getDistanceAlongPolyline(double[] point, List<double[]> polyline, int startIndex) {
        if (point == null || polyline == null || polyline.isEmpty() || startIndex < 0 || startIndex >= polyline.size()) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        double pointLon = point[0];
        double pointLat = point[1];
        
        // Find the segment closest to the point
        int nearestSegmentIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < polyline.size() - 1; i++) {
            double[] segmentStart = polyline.get(i);
            double[] segmentEnd = polyline.get(i + 1);
            
            double distanceToSegment = distanceToLineSegment(
                pointLat, pointLon,
                segmentStart[1], segmentStart[0],
                segmentEnd[1], segmentEnd[0]
            );
            
            if (distanceToSegment < minDistance) {
                minDistance = distanceToSegment;
                nearestSegmentIndex = i;
            }
        }
        
        // Calculate distance from start to the nearest segment
        for (int i = startIndex; i < nearestSegmentIndex; i++) {
            if (i < polyline.size() - 1) {
                double[] segStart = polyline.get(i);
                double[] segEnd = polyline.get(i + 1);
                totalDistance += calculateHaversineDistance(
                    segStart[1], segStart[0],
                    segEnd[1], segEnd[0]
                );
            }
        }
        
        // Add distance along the nearest segment to the point
        if (nearestSegmentIndex < polyline.size() - 1) {
            double[] segStart = polyline.get(nearestSegmentIndex);
            double[] segEnd = polyline.get(nearestSegmentIndex + 1);
            // Approximate: use distance to segment start as proxy
            totalDistance += calculateHaversineDistance(
                segStart[1], segStart[0],
                pointLat, pointLon
            );
        }
        
        return totalDistance * 1000.0; // Convert to meters
    }
    
    /**
     * Overloaded method with configured threshold.
     */
    public boolean isPointNearPolyline(double[] point, List<double[]> polyline) {
        return isPointNearPolyline(point, polyline, maxDistanceMeters);
    }
    
    /**
     * Find the index of the nearest point on the polyline to the given point.
     * SIMPLIFIED: Uses point-to-point distance only (no complex segment logic).
     * This ensures correct ordering and avoids misdetection issues.
     * 
     * @param point Point as [longitude, latitude]
     * @param polyline List of polyline points, each as [longitude, latitude]
     * @return Index of nearest point on polyline, or -1 if polyline is empty
     */
    public int findNearestPolylinePointIndex(double[] point, List<double[]> polyline) {
        if (point == null || point.length < 2 || polyline == null || polyline.isEmpty()) {
            return -1;
        }
        
        int nearestIndex = -1;
        double minDistance = Double.MAX_VALUE;
        
        // Simple point-to-point distance check (no segment logic to avoid ordering issues)
        for (int i = 0; i < polyline.size(); i++) {
            double[] p = polyline.get(i);
            // point is [lon, lat], p is [lon, lat]
            // calculateHaversineDistance expects (lat, lon, lat, lon)
            double dist = calculateHaversineDistance(point[1], point[0], p[1], p[0]);
            
            if (dist < minDistance) {
                minDistance = dist;
                nearestIndex = i;
            }
        }
        
        log.debug("Nearest point index for [{}, {}] is {} (distance: {} km)", 
            point[0], point[1], nearestIndex, String.format("%.4f", minDistance));
        
        return nearestIndex;
    }
    
    /**
     * Calculate distance from a point to a line segment using Haversine formula.
     * 
     * @param pointLat Latitude of the point
     * @param pointLon Longitude of the point
     * @param segStartLat Latitude of segment start
     * @param segStartLon Longitude of segment start
     * @param segEndLat Latitude of segment end
     * @param segEndLon Longitude of segment end
     * @return Distance in kilometers
     */
    private double distanceToLineSegment(
            double pointLat, double pointLon,
            double segStartLat, double segStartLon,
            double segEndLat, double segEndLon) {
        
        // Calculate distance from point to segment start
        double distToStart = calculateHaversineDistance(pointLat, pointLon, segStartLat, segStartLon);
        
        // Calculate distance from point to segment end
        double distToEnd = calculateHaversineDistance(pointLat, pointLon, segEndLat, segEndLon);
        
        // Calculate distance of the segment itself
        double segmentLength = calculateHaversineDistance(segStartLat, segStartLon, segEndLat, segEndLon);
        
        // If segment is very short, just return distance to nearest endpoint
        if (segmentLength < 0.001) { // Less than 1 meter
            return Math.min(distToStart, distToEnd);
        }
        
        // Calculate the perpendicular distance from point to line segment
        // Using the formula: distance = |(y2-y1)x0 - (x2-x1)y0 + x2*y1 - y2*x1| / sqrt((y2-y1)^2 + (x2-x1)^2)
        // But we need to work in a local coordinate system for accuracy
        
        // For small distances, we can approximate using a simple projection
        // Convert lat/lon to approximate meters for local calculation
        double latToMeters = 111320.0; // 1 degree latitude ≈ 111.32 km
        double lonToMeters = 111320.0 * Math.cos(Math.toRadians((segStartLat + segEndLat) / 2.0));
        
        double dx1 = (segEndLon - segStartLon) * lonToMeters;
        double dy1 = (segEndLat - segStartLat) * latToMeters;
        double dx2 = (pointLon - segStartLon) * lonToMeters;
        double dy2 = (pointLat - segStartLat) * latToMeters;
        
        double dotProduct = dx1 * dx2 + dy1 * dy2;
        double segmentLengthSquared = dx1 * dx1 + dy1 * dy1;
        
        if (segmentLengthSquared < 0.0001) {
            return distToStart;
        }
        
        double t = Math.max(0.0, Math.min(1.0, dotProduct / segmentLengthSquared));
        
        // Closest point on segment
        double closestLon = segStartLon + t * (segEndLon - segStartLon);
        double closestLat = segStartLat + t * (segEndLat - segStartLat);
        
        // Distance to closest point
        return calculateHaversineDistance(pointLat, pointLon, closestLat, closestLon);
    }
    
    /**
     * Calculate Haversine distance between two points.
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Check if passenger route lies along driver's route polyline.
     * 
     * @param passengerSource Point as [longitude, latitude]
     * @param passengerDestination Point as [longitude, latitude]
     * @param driverRouteGeometry JSON string of driver's route geometry
     * @return true if both passenger points are near the polyline and source comes before destination
     */
    public boolean isPassengerRouteAlongDriverPolyline(
            double[] passengerSource,
            double[] passengerDestination,
            String driverRouteGeometry) {
        
        if (passengerSource == null || passengerDestination == null || driverRouteGeometry == null) {
            log.debug("❌ Invalid input: passengerSource={}, passengerDestination={}, driverRouteGeometry={}", 
                passengerSource != null, passengerDestination != null, driverRouteGeometry != null);
            return false;
        }
        
        List<double[]> polyline = parseRouteGeometry(driverRouteGeometry);
        if (polyline.isEmpty()) {
            log.error("❌❌❌ CRITICAL: Driver route geometry is empty after parsing, cannot perform partial route matching");
            log.error("   Geometry JSON length: {} chars", driverRouteGeometry != null ? driverRouteGeometry.length() : 0);
            if (driverRouteGeometry != null && driverRouteGeometry.length() > 0) {
                log.error("   Geometry JSON preview (first 500 chars): {}", 
                    driverRouteGeometry.length() > 500 ? driverRouteGeometry.substring(0, 500) + "..." : driverRouteGeometry);
            }
            return false;
        }
        
        log.info("🔍🔍🔍 POLYLINE MATCHING START - polyline has {} points", polyline.size());
        log.info("   Passenger source: [lon={}, lat={}]", passengerSource[0], passengerSource[1]);
        log.info("   Passenger destination: [lon={}, lat={}]", passengerDestination[0], passengerDestination[1]);
        if (polyline.size() > 0) {
            log.info("   Driver route starts: [lon={}, lat={}], ends: [lon={}, lat={}]", 
                polyline.get(0)[0], polyline.get(0)[1],
                polyline.get(polyline.size() - 1)[0], polyline.get(polyline.size() - 1)[1]);
            // Log first few and last few points for debugging
            if (polyline.size() > 4) {
                log.info("   First 3 polyline points: [{}], [{}], [{}]", 
                    java.util.Arrays.toString(polyline.get(0)),
                    java.util.Arrays.toString(polyline.get(1)),
                    java.util.Arrays.toString(polyline.get(2)));
                log.info("   Last 3 polyline points: [{}], [{}], [{}]", 
                    java.util.Arrays.toString(polyline.get(polyline.size() - 3)),
                    java.util.Arrays.toString(polyline.get(polyline.size() - 2)),
                    java.util.Arrays.toString(polyline.get(polyline.size() - 1)));
            }
        }
        
        // Get distances to polyline
        log.info("   Calculating distances from passenger points to polyline...");
        double sourceMinDistance = getMinDistanceToPolyline(passengerSource, polyline);
        double destMinDistance = getMinDistanceToPolyline(passengerDestination, polyline);
        
        log.info("   📏 Distance results - Source: {}m, Destination: {}m (threshold: {}m)", 
            String.format("%.2f", sourceMinDistance), 
            String.format("%.2f", destMinDistance), 
            maxDistanceMeters);
        
        // CRITICAL: Detect coordinate swap issues (distances > 1000km indicate lat/lon swapped)
        if (sourceMinDistance > 1000000 || destMinDistance > 1000000) {
            log.error("🚨🚨🚨 CRITICAL: Suspiciously large distance to polyline detected!");
            log.error("   Source distance: {}m ({}km), Destination distance: {}m ({}km)", 
                String.format("%.2f", sourceMinDistance), String.format("%.2f", sourceMinDistance / 1000.0),
                String.format("%.2f", destMinDistance), String.format("%.2f", destMinDistance / 1000.0));
            log.error("   This usually means LAT/LON are swapped in route geometry or passenger coordinates!");
            log.error("   Passenger source: [lon={}, lat={}], Passenger dest: [lon={}, lat={}]", 
                passengerSource[0], passengerSource[1], passengerDestination[0], passengerDestination[1]);
            if (polyline.size() > 0) {
                log.error("   Driver route first point: [lon={}, lat={}], last point: [lon={}, lat={}]", 
                    polyline.get(0)[0], polyline.get(0)[1],
                    polyline.get(polyline.size() - 1)[0], polyline.get(polyline.size() - 1)[1]);
            }
            log.error("   Please check: 1) Route geometry format, 2) Geocoding return format, 3) Coordinate parsing");
        }
        
        // Check if passenger source is near the polyline (with configurable threshold)
        boolean sourceNearRoute = sourceMinDistance <= maxDistanceMeters;
        log.info("   Passenger source near route: {} (min distance: {}m, threshold: {}m)", 
            sourceNearRoute, String.format("%.2f", sourceMinDistance), maxDistanceMeters);
        
        // Check if passenger destination is near the polyline
        boolean destNearRoute = destMinDistance <= maxDistanceMeters;
        log.info("   Passenger destination near route: {} (min distance: {}m, threshold: {}m)", 
            destNearRoute, String.format("%.2f", destMinDistance), maxDistanceMeters);
        
        // CRITICAL DEBUG: If distances are close to threshold, log detailed info
        if (sourceMinDistance > maxDistanceMeters * 0.8 || destMinDistance > maxDistanceMeters * 0.8) {
            log.warn("   ⚠️ Distances are close to threshold - Source: {}% of threshold, Dest: {}% of threshold", 
                String.format("%.1f", (sourceMinDistance / maxDistanceMeters) * 100),
                String.format("%.1f", (destMinDistance / maxDistanceMeters) * 100));
        }
        
        // SPECIAL CASE: If one endpoint is very close (within 1km), be more lenient with the other
        // This handles cases where passenger starts/ends at driver's exact start/end point
        double lenientThreshold = maxDistanceMeters * 1.5; // 1.5x configured threshold
        if (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) {
            log.info("   One endpoint is very close (within 1km), using lenient threshold: {}m", lenientThreshold);
            sourceNearRoute = sourceMinDistance <= lenientThreshold;
            destNearRoute = destMinDistance <= lenientThreshold;
        }
        
        if (!sourceNearRoute) {
            log.info("❌ Passenger source [lon={}, lat={}] is {}m from driver's route polyline (threshold: {}m)", 
                passengerSource[0], passengerSource[1], String.format("%.2f", sourceMinDistance), 
                (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) ? lenientThreshold : maxDistanceMeters);
            return false;
        }
        
        if (!destNearRoute) {
            log.info("❌ Passenger destination [lon={}, lat={}] is {}m from driver's route polyline (threshold: {}m)", 
                passengerDestination[0], passengerDestination[1], String.format("%.2f", destMinDistance),
                (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) ? lenientThreshold : maxDistanceMeters);
            return false;
        }
        
        // Find nearest indices to validate order
        int sourceIndex = findNearestPolylinePointIndex(passengerSource, polyline);
        int destIndex = findNearestPolylinePointIndex(passengerDestination, polyline);
        
        log.info("   Nearest polyline indices - Source: {}, Destination: {}", sourceIndex, destIndex);
        
        // FIX C: Relaxed ordering logic - don't kill match purely because of weird index ordering
        // when both endpoints are clearly near the route
        
        // SPECIAL CASE: If both endpoints are near route but indices are invalid, accept match
        if (sourceNearRoute && destNearRoute && (sourceIndex < 0 || destIndex < 0)) {
            log.info("✅ Both endpoints are near route but indices invalid - accepting match (index-based ordering skipped)");
            return true;
        }
        
        // Validate that source comes before destination along the route
        // FIX C: Only enforce strict ordering if indices are far apart and reliable
        boolean validOrder = true; // Default to true (lenient)
        
        // Only enforce ordering if indices are far apart and reliable
        if (sourceIndex >= 0 && destIndex >= 0 && Math.abs(sourceIndex - destIndex) > 2) {
            // Indices are far apart - use strict ordering
            validOrder = sourceIndex < destIndex;
            log.info("   Indices far apart ({} vs {}), using strict index-based ordering: validOrder={}", 
                sourceIndex, destIndex, validOrder);
        } else {
            // Indices are close together or invalid - use distance-based ordering
            if (sourceIndex == destIndex || Math.abs(sourceIndex - destIndex) <= 5) {
                // Both points are near the same segment, check actual distances along route
                double sourceDistFromStart = getDistanceAlongPolyline(passengerSource, polyline, 0);
                double destDistFromStart = getDistanceAlongPolyline(passengerDestination, polyline, 0);
                
                // Allow small tolerance (500m) for measurement errors
                double toleranceMeters = 500.0;
                validOrder = sourceDistFromStart < destDistFromStart + toleranceMeters;
                
                log.info("   Indices close ({} vs {}), using distance-based ordering: source={}m, dest={}m, tolerance={}m, validOrder={}", 
                    sourceIndex, destIndex, 
                    String.format("%.2f", sourceDistFromStart), 
                    String.format("%.2f", destDistFromStart),
                    toleranceMeters,
                    validOrder);
            } else {
                // Indices are close but not equal - be lenient
                log.info("   Indices are close ({} vs {}), being lenient with ordering", sourceIndex, destIndex);
                validOrder = true; // Accept match if both points are near route
            }
        }
        
        // SPECIAL CASE: Allow reverse order if passenger route is very short
        // This handles cases like: Driver: A→B→C, Passenger: C→B (short reverse segment)
        if (!validOrder && sourceIndex >= destIndex) {
            double passengerRouteDistance = calculateHaversineDistance(
                passengerSource[1], passengerSource[0],
                passengerDestination[1], passengerDestination[0]
            ) * 1000.0; // Convert to meters
            
            // Calculate total driver route distance
            double driverRouteDistance = 0.0;
            for (int i = 0; i < polyline.size() - 1; i++) {
                driverRouteDistance += calculateHaversineDistance(
                    polyline.get(i)[1], polyline.get(i)[0],
                    polyline.get(i + 1)[1], polyline.get(i + 1)[0]
                ) * 1000.0;
            }
            
            // If passenger route is a small reverse segment (less than 20% of driver route), allow it
            if (passengerRouteDistance < driverRouteDistance * 0.2 && passengerRouteDistance < 50000.0) {
                log.info("   Allowing reverse short segment: passenger route {}m is {}% of driver route {}m", 
                    String.format("%.2f", passengerRouteDistance),
                    String.format("%.1f", (passengerRouteDistance / driverRouteDistance) * 100),
                    String.format("%.2f", driverRouteDistance));
                validOrder = true; // Allow reverse order for short segments
            }
        }
        
        if (validOrder) {
            log.info("✅ Partial route match: Passenger source (index {}) comes before destination (index {})", 
                sourceIndex, destIndex);
        } else {
            log.info("❌ Invalid route order: Passenger source (index {}) does not come before destination (index {})", 
                sourceIndex, destIndex);
        }
        
        return validOrder;
    }
    
    /**
     * Generate a synthetic polyline from driver's source to destination.
     * Creates intermediate waypoints along a great circle path for better matching.
     * Used as fallback when route geometry is not available.
     * 
     * @param driverSourceLat Driver source latitude
     * @param driverSourceLon Driver source longitude
     * @param driverDestLat Driver destination latitude
     * @param driverDestLon Driver destination longitude
     * @param numWaypoints Number of intermediate waypoints to generate
     * @return List of coordinate arrays [longitude, latitude] representing synthetic polyline
     */
    public List<double[]> generateSyntheticPolyline(
            double driverSourceLat, double driverSourceLon,
            double driverDestLat, double driverDestLon,
            int numWaypoints) {
        
        List<double[]> polyline = new ArrayList<>();
        
        // Add source point
        polyline.add(new double[]{driverSourceLon, driverSourceLat});
        
        // Generate intermediate waypoints along great circle path
        for (int i = 1; i < numWaypoints; i++) {
            double fraction = (double) i / numWaypoints;
            
            // Interpolate along great circle (not straight line - accounts for Earth's curvature)
            double[] waypoint = interpolateGreatCircle(
                driverSourceLat, driverSourceLon,
                driverDestLat, driverDestLon,
                fraction
            );
            polyline.add(waypoint);
        }
        
        // Add destination point
        polyline.add(new double[]{driverDestLon, driverDestLat});
        
        log.info("✅ Generated synthetic polyline with {} points (including {} waypoints)", 
            polyline.size(), numWaypoints - 1);
        
        return polyline;
    }
    
    /**
     * Interpolate a point along a great circle path between two points.
     * Uses spherical linear interpolation (slerp) for accurate results on Earth's surface.
     * 
     * @param lat1 Start latitude
     * @param lon1 Start longitude
     * @param lat2 End latitude
     * @param lon2 End longitude
     * @param fraction Fraction along path (0.0 = start, 1.0 = end)
     * @return Interpolated point as [longitude, latitude]
     */
    private double[] interpolateGreatCircle(double lat1, double lon1, double lat2, double lon2, double fraction) {
        // Convert to radians
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        
        // Calculate angular distance
        double d = Math.acos(
            Math.sin(lat1Rad) * Math.sin(lat2Rad) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.cos(lon2Rad - lon1Rad)
        );
        
        if (d < 0.0001) {
            // Points are very close, return start point
            return new double[]{lon1, lat1};
        }
        
        // Spherical linear interpolation
        double a = Math.sin((1 - fraction) * d) / Math.sin(d);
        double b = Math.sin(fraction * d) / Math.sin(d);
        
        double x = a * Math.cos(lat1Rad) * Math.cos(lon1Rad) + b * Math.cos(lat2Rad) * Math.cos(lon2Rad);
        double y = a * Math.cos(lat1Rad) * Math.sin(lon1Rad) + b * Math.cos(lat2Rad) * Math.sin(lon2Rad);
        double z = a * Math.sin(lat1Rad) + b * Math.sin(lat2Rad);
        
        double lat = Math.atan2(z, Math.sqrt(x * x + y * y));
        double lon = Math.atan2(y, x);
        
        return new double[]{Math.toDegrees(lon), Math.toDegrees(lat)};
    }
}
