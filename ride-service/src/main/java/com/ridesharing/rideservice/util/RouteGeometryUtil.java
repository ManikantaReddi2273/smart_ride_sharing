package com.ridesharing.rideservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
    private static final double MAX_DISTANCE_METERS = 50000.0; // 50 km threshold (required for Indian cities which are 20-40km apart)
    
    private final ObjectMapper objectMapper;
    
    public RouteGeometryUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            log.warn("⚠️ Route geometry JSON is null or empty");
            return coordinates;
        }
        
        try {
            JsonNode root = objectMapper.readTree(routeGeometryJson);
            if (root.isArray()) {
                for (JsonNode coordNode : root) {
                    if (coordNode.isArray() && coordNode.size() >= 2) {
                        double lon = coordNode.get(0).asDouble();
                        double lat = coordNode.get(1).asDouble();
                        coordinates.add(new double[]{lon, lat});
                    }
                }
                log.debug("✅ Parsed {} coordinate points from route geometry", coordinates.size());
            } else {
                log.warn("⚠️ Route geometry JSON is not an array: {}", 
                    routeGeometryJson.length() > 100 ? routeGeometryJson.substring(0, 100) + "..." : routeGeometryJson);
            }
        } catch (Exception ex) {
            log.error("❌ Failed to parse route geometry JSON: {}", ex.getMessage(), ex);
            log.error("   JSON content (first 200 chars): {}", 
                routeGeometryJson.length() > 200 ? routeGeometryJson.substring(0, 200) + "..." : routeGeometryJson);
        }
        
        if (coordinates.isEmpty()) {
            log.warn("⚠️ Parsed route geometry resulted in empty coordinate list");
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
     */
    private double getMinDistanceToPolyline(double[] point, List<double[]> polyline) {
        if (point == null || point.length < 2 || polyline == null || polyline.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        double minDistance = Double.MAX_VALUE;
        double pointLon = point[0];
        double pointLat = point[1];
        
        for (int i = 0; i < polyline.size() - 1; i++) {
            double[] segmentStart = polyline.get(i);
            double[] segmentEnd = polyline.get(i + 1);
            
            double distanceToSegment = distanceToLineSegment(
                pointLat, pointLon,
                segmentStart[1], segmentStart[0],
                segmentEnd[1], segmentEnd[0]
            );
            
            double distanceMeters = distanceToSegment * 1000.0;
            if (distanceMeters < minDistance) {
                minDistance = distanceMeters;
            }
        }
        
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
     * Overloaded method with default threshold.
     */
    public boolean isPointNearPolyline(double[] point, List<double[]> polyline) {
        return isPointNearPolyline(point, polyline, MAX_DISTANCE_METERS);
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
            log.warn("❌ Driver route geometry is empty after parsing, cannot perform partial route matching");
            log.warn("   Geometry JSON length: {} chars", driverRouteGeometry != null ? driverRouteGeometry.length() : 0);
            return false;
        }
        
        log.info("🔍 Checking polyline matching - polyline has {} points", polyline.size());
        log.info("   Passenger source: [lon={}, lat={}]", passengerSource[0], passengerSource[1]);
        log.info("   Passenger destination: [lon={}, lat={}]", passengerDestination[0], passengerDestination[1]);
        if (polyline.size() > 0) {
            log.info("   Driver route starts: [lon={}, lat={}], ends: [lon={}, lat={}]", 
                polyline.get(0)[0], polyline.get(0)[1],
                polyline.get(polyline.size() - 1)[0], polyline.get(polyline.size() - 1)[1]);
        }
        
        // Get distances to polyline
        double sourceMinDistance = getMinDistanceToPolyline(passengerSource, polyline);
        double destMinDistance = getMinDistanceToPolyline(passengerDestination, polyline);
        
        // Check if passenger source is near the polyline (with increased threshold for flexibility)
        boolean sourceNearRoute = sourceMinDistance <= MAX_DISTANCE_METERS;
        log.info("   Passenger source near route: {} (min distance: {}m, threshold: {}m)", 
            sourceNearRoute, String.format("%.2f", sourceMinDistance), MAX_DISTANCE_METERS);
        
        // Check if passenger destination is near the polyline
        boolean destNearRoute = destMinDistance <= MAX_DISTANCE_METERS;
        log.info("   Passenger destination near route: {} (min distance: {}m, threshold: {}m)", 
            destNearRoute, String.format("%.2f", destMinDistance), MAX_DISTANCE_METERS);
        
        // SPECIAL CASE: If one endpoint is very close (within 1km), be more lenient with the other
        // This handles cases where passenger starts/ends at driver's exact start/end point
        double lenientThreshold = MAX_DISTANCE_METERS * 1.5; // 75km if one point is very close (50km * 1.5)
        if (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) {
            log.info("   One endpoint is very close (within 1km), using lenient threshold: {}m", lenientThreshold);
            sourceNearRoute = sourceMinDistance <= lenientThreshold;
            destNearRoute = destMinDistance <= lenientThreshold;
        }
        
        if (!sourceNearRoute) {
            log.info("❌ Passenger source [lon={}, lat={}] is {}m from driver's route polyline (threshold: {}m)", 
                passengerSource[0], passengerSource[1], String.format("%.2f", sourceMinDistance), 
                (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) ? lenientThreshold : MAX_DISTANCE_METERS);
            return false;
        }
        
        if (!destNearRoute) {
            log.info("❌ Passenger destination [lon={}, lat={}] is {}m from driver's route polyline (threshold: {}m)", 
                passengerDestination[0], passengerDestination[1], String.format("%.2f", destMinDistance),
                (sourceMinDistance <= 1000.0 || destMinDistance <= 1000.0) ? lenientThreshold : MAX_DISTANCE_METERS);
            return false;
        }
        
        // Find nearest indices to validate order
        int sourceIndex = findNearestPolylinePointIndex(passengerSource, polyline);
        int destIndex = findNearestPolylinePointIndex(passengerDestination, polyline);
        
        log.info("   Nearest polyline indices - Source: {}, Destination: {}", sourceIndex, destIndex);
        
        // Validate that source comes before destination along the route
        // Allow some flexibility: if indices are very close, check actual distances
        boolean validOrder = sourceIndex < destIndex;
        
        // If indices are equal or very close, use distance-based ordering
        if (sourceIndex == destIndex || Math.abs(sourceIndex - destIndex) <= 3) {
            // Both points are near the same segment, check actual distances along route
            double sourceDistFromStart = getDistanceAlongPolyline(passengerSource, polyline, 0);
            double destDistFromStart = getDistanceAlongPolyline(passengerDestination, polyline, 0);
            validOrder = sourceDistFromStart < destDistFromStart;
            log.info("   Indices too close ({} vs {}), using distance-based ordering: source={}m, dest={}m", 
                sourceIndex, destIndex, String.format("%.2f", sourceDistFromStart), String.format("%.2f", destDistFromStart));
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
}
