package com.ridesharing.rideservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Service (OpenRouteService)
 * <p>
 * Uses OpenRouteService Directions API to calculate distance and duration
 * between two locations. OpenRouteService is a free, open-source routing service.
 */
@Service
@Slf4j
public class GoogleMapsService {

    @Value("${google.maps.api.key:}")
    private String apiKey;

    @Value("${google.maps.distance-matrix.url:https://api.openrouteservice.org/v2/directions/driving-car}")
    private String directionsApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GoogleMapsService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculate distance and duration using coordinates directly (FASTEST & MOST ACCURATE)
     * This method skips geocoding and uses exact coordinates from frontend autocomplete
     *
     * @param sourceLat      Source latitude
     * @param sourceLon      Source longitude
     * @param destLat        Destination latitude
     * @param destLon        Destination longitude
     * @param sourceName     Source location name (for logging)
     * @param destName       Destination location name (for logging)
     * @return DistanceMatrixResult containing distance (km) and duration
     */
    public DistanceMatrixResult calculateDistanceFromCoordinates(
            Double sourceLat, Double sourceLon, Double destLat, Double destLon,
            String sourceName, String destName) {
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouteService API key is not configured.");
        }
        
        // Validate coordinates are provided
        if (sourceLat == null || sourceLon == null || destLat == null || destLon == null) {
            throw new IllegalArgumentException("All coordinates must be provided");
        }
        
        // Validate coordinates are within valid range
        if (sourceLon < -180 || sourceLon > 180 || sourceLat < -90 || sourceLat > 90 ||
            destLon < -180 || destLon > 180 || destLat < -90 || destLat > 90) {
            throw new IllegalArgumentException("Invalid coordinates provided");
        }
        
        // Validate coordinates are within India
        if (sourceLon < 68 || sourceLon > 97 || sourceLat < 6 || sourceLat > 37) {
            log.error("⚠️ SOURCE COORDINATES OUTSIDE INDIA: [lon={}, lat={}] for '{}'", 
                sourceLon, sourceLat, sourceName);
            throw new RuntimeException("Source coordinates are outside India. Please select a valid Indian location.");
        }
        if (destLon < 68 || destLon > 97 || destLat < 6 || destLat > 37) {
            log.error("⚠️ DESTINATION COORDINATES OUTSIDE INDIA: [lon={}, lat={}] for '{}'", 
                destLon, destLat, destName);
            throw new RuntimeException("Destination coordinates are outside India. Please select a valid Indian location.");
        }
        
        log.info("✅ Using coordinates directly (skipping geocoding) - Source '{}': [lon={}, lat={}], Destination '{}': [lon={}, lat={}]", 
            sourceName, sourceLon, sourceLat, destName, destLon, destLat);
        
        String[] sourceCoords = new String[]{String.valueOf(sourceLon), String.valueOf(sourceLat)};
        String[] destCoords = new String[]{String.valueOf(destLon), String.valueOf(destLat)};
        
        return calculateDistanceFromCoordinates(sourceCoords, destCoords, sourceName, destName);
    }
    
    /**
     * Calculate distance and duration between source and destination using
     * OpenRouteService Directions API.
     * This method geocodes addresses first, then calculates distance.
     * Use calculateDistanceFromCoordinates() if you already have coordinates (faster & more accurate).
     *
     * @param source      Origin address (e.g., "New York, NY")
     * @param destination Destination address (e.g., "Boston, MA")
     * @return DistanceMatrixResult containing distance (km) and duration
     */
    public DistanceMatrixResult calculateDistance(String source, String destination) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouteService API key is not configured. Please set 'google.maps.api.key' property.");
        }

        // CRITICAL: Log what frontend is sending to identify wrong inputs
        log.info("🔍 INCOMING FRONTEND REQUEST - Source: '{}', Destination: '{}'", source, destination);
        
        // Validate inputs are not empty or suspicious
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source address cannot be empty");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination address cannot be empty");
        }
        
        // Check for suspicious patterns (multiple countries, wrong regions)
        String sourceLower = source.toLowerCase();
        String destLower = destination.toLowerCase();
        if (sourceLower.contains("europe") || sourceLower.contains("germany") || 
            sourceLower.contains("usa") || sourceLower.contains("united states") ||
            sourceLower.contains("australia") || sourceLower.contains("new zealand")) {
            log.error("⚠️ SUSPICIOUS SOURCE ADDRESS DETECTED: '{}' - This suggests wrong autocomplete selection!", source);
            throw new RuntimeException("Invalid source address detected. Please select a location from India only.");
        }
        if (destLower.contains("europe") || destLower.contains("germany") || 
            destLower.contains("usa") || destLower.contains("united states") ||
            destLower.contains("australia") || destLower.contains("new zealand")) {
            log.error("⚠️ SUSPICIOUS DESTINATION ADDRESS DETECTED: '{}' - This suggests wrong autocomplete selection!", destination);
            throw new RuntimeException("Invalid destination address detected. Please select a location from India only.");
        }

        try {
            // Step 1: Geocode source and destination addresses
            log.info("Starting geocoding for source: '{}' and destination: '{}'", source, destination);
            String[] sourceCoords = geocodeAddress(source);
            String[] destCoords = geocodeAddress(destination);
            
            // Validate coordinates before proceeding
            if (sourceCoords == null || sourceCoords.length != 2) {
                throw new RuntimeException("Failed to geocode source address: '" + source + "'");
            }
            if (destCoords == null || destCoords.length != 2) {
                throw new RuntimeException("Failed to geocode destination address: '" + destination + "'");
            }
            
            double sourceLon = Double.parseDouble(sourceCoords[0]);
            double sourceLat = Double.parseDouble(sourceCoords[1]);
            double destLon = Double.parseDouble(destCoords[0]);
            double destLat = Double.parseDouble(destCoords[1]);
            
            // Validate coordinates are within India
            if (sourceLon < 68 || sourceLon > 97 || sourceLat < 6 || sourceLat > 37) {
                log.error("⚠️ SOURCE COORDINATES OUTSIDE INDIA: [lon={}, lat={}] for address '{}'", 
                    sourceLon, sourceLat, source);
                throw new RuntimeException("Source coordinates are outside India. Please select a valid Indian location.");
            }
            if (destLon < 68 || destLon > 97 || destLat < 6 || destLat > 37) {
                log.error("⚠️ DESTINATION COORDINATES OUTSIDE INDIA: [lon={}, lat={}] for address '{}'", 
                    destLon, destLat, destination);
                throw new RuntimeException("Destination coordinates are outside India. Please select a valid Indian location.");
            }
            
            // Calculate straight-line distance immediately to validate geocoding
            double preliminaryDistance = calculateHaversineDistance(sourceLat, sourceLon, destLat, destLon);
            log.info("✅ Geocoded addresses - Source '{}': [lon={}, lat={}], Destination '{}': [lon={}, lat={}]", 
                source, sourceLon, sourceLat, destination, destLon, destLat);
            log.info("Preliminary straight-line distance: {} km", String.format("%.2f", preliminaryDistance));
            
            // Step 2: Use Directions API to get accurate distance
            return calculateDistanceFromCoordinates(sourceCoords, destCoords, source, destination);
            
        } catch (RuntimeException ex) {
            // Re-throw RuntimeExceptions (these are our custom errors with proper messages)
            log.error("Error while calling OpenRouteService API: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error while calling OpenRouteService API: {}", ex.getMessage(), ex);
            // Check if it's a connection/network error
            if (ex.getMessage() != null && (ex.getMessage().contains("Connection") || ex.getMessage().contains("timeout"))) {
                throw new RuntimeException("Unable to connect to OpenRouteService API. Please check your internet connection.", ex);
            }
            throw new RuntimeException("Failed to calculate distance: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Geocode an address to coordinates using OpenRouteService Geocoding API
     * Public method for use by other services (e.g., RideService for route matching)
     * Uses country restriction for India to ensure accurate results
     * @param address Address to geocode
     * @return Array with [longitude, latitude] or null if geocoding fails
     */
    public String[] geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be empty");
        }
        
        String normalizedAddress = address.trim();
        
        // Simple normalization: expand common abbreviations
        normalizedAddress = normalizeAddress(normalizedAddress);
        
        try {
            String[] coords = geocodeAddressDirect(normalizedAddress);
            log.info("Successfully geocoded '{}' to coordinates: [lon={}, lat={}]", 
                normalizedAddress, coords[0], coords[1]);
            return coords;
        } catch (Exception ex) {
            log.error("Geocoding failed for '{}': {}", normalizedAddress, ex.getMessage());
            throw new RuntimeException("Location not found: '" + normalizedAddress + 
                "'. Please check the spelling or try a more specific address.", ex);
        }
    }
    
    /**
     * Normalize address by expanding common abbreviations
     * Only does essential expansions to avoid wrong matches
     */
    private String normalizeAddress(String address) {
        String normalized = address;
        
        // Expand state abbreviations (only when clearly part of address)
        if (normalized.contains(", AP") || normalized.contains(",AP") || normalized.endsWith(" AP")) {
            normalized = normalized.replace(", AP", ", Andhra Pradesh")
                                  .replace(",AP", ", Andhra Pradesh")
                                  .replace(" AP", ", Andhra Pradesh");
        }
        
        // Expand common city abbreviations
        if (normalized.contains("Vizag")) {
            normalized = normalized.replace("Vizag", "Visakhapatnam");
        }
        if (normalized.contains("Hyd,") || normalized.contains("Hyd ")) {
            normalized = normalized.replace("Hyd,", "Hyderabad,").replace("Hyd ", "Hyderabad ");
        }
        
        // Ensure India is present if not already there
        if (!normalized.toLowerCase().contains("india") && 
            !normalized.toLowerCase().contains("ind")) {
            // Only add if it looks like an Indian address (has state or common Indian city)
            if (normalized.contains("Andhra Pradesh") || normalized.contains("Maharashtra") ||
                normalized.contains("Karnataka") || normalized.contains("Tamil Nadu") ||
                normalized.contains("Delhi") || normalized.contains("Mumbai") ||
                normalized.contains("Bangalore") || normalized.contains("Chennai")) {
                normalized = normalized + ", India";
            }
        }
        
        return normalized.trim();
    }
    
    /**
     * Direct geocoding with country restriction for India
     * This ensures we get the correct Indian location, not foreign matches
     */
    private String[] geocodeAddressDirect(String address) {
        try {
            String geocodeUrl = "https://api.openrouteservice.org/geocode/search";
            
            HttpHeaders headers = new HttpHeaders();
            String authHeader = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
            headers.set("Authorization", authHeader);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            
            // CRITICAL: Use country restriction to get accurate Indian locations
            // This prevents wrong matches like "Neuss, Germany" for "Nuzvid"
            String encodedAddress = java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);
            String url = geocodeUrl + "?text=" + encodedAddress + 
                        "&boundary.country=IN" +  // RESTRICT TO INDIA - prevents foreign matches
                        "&size=1";                // Only need first result (most accurate)
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.debug("Geocoding address: '{}'", address);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Geocoding API returned status: " + response.getStatusCode());
            }
            
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                throw new RuntimeException("Geocoding API error: " + errorMsg);
            }
            
            JsonNode features = root.path("features");
            if (!features.isArray() || features.size() == 0) {
                throw new RuntimeException("No location found for: " + address);
            }
            
            // Get first result (most accurate match)
            JsonNode firstFeature = features.get(0);
            JsonNode geometry = firstFeature.path("geometry");
            JsonNode coordinates = geometry.path("coordinates");
            
            if (!coordinates.isArray() || coordinates.size() < 2) {
                throw new RuntimeException("Invalid coordinates in geocoding response");
            }
            
            // OpenRouteService returns [longitude, latitude]
            String longitude = String.valueOf(coordinates.get(0).asDouble());
            String latitude = String.valueOf(coordinates.get(1).asDouble());
            
            // Validate coordinates are reasonable
            double lon = Double.parseDouble(longitude);
            double lat = Double.parseDouble(latitude);
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                throw new RuntimeException("Invalid coordinates: [" + lon + ", " + lat + "]");
            }
            
            String label = firstFeature.path("properties").path("label").asText(address);
            log.debug("Geocoded '{}' to '{}' at coordinates: [lon={}, lat={}]", address, label, longitude, latitude);
            
            return new String[]{longitude, latitude};
            
        } catch (Exception ex) {
            log.error("Error geocoding address '{}': {}", address, ex.getMessage());
            throw new RuntimeException("Geocoding failed for: " + address, ex);
        }
    }
    
    /**
     * @deprecated This method is no longer used. Replaced by simplified geocodeAddressDirect()
     * Kept for reference but will be removed in future versions.
     */
    @Deprecated
    private java.util.List<String> generateAddressVariations(String address) {
        java.util.List<String> variations = new java.util.ArrayList<>();
        
        // Add original address first
        variations.add(address);
        
        // Common Indian city/location abbreviations and expansions
        java.util.Map<String, String> cityAbbreviations = new java.util.HashMap<>();
        cityAbbreviations.put("Vizag", "Visakhapatnam");
        cityAbbreviations.put("Vizag ", "Visakhapatnam ");
        cityAbbreviations.put("Vizag,", "Visakhapatnam,");
        cityAbbreviations.put("Vizag.", "Visakhapatnam.");
        cityAbbreviations.put("Hyd", "Hyderabad");
        cityAbbreviations.put("Hyd ", "Hyderabad ");
        cityAbbreviations.put("Hyd,", "Hyderabad,");
        cityAbbreviations.put("B'lore", "Bangalore");
        cityAbbreviations.put("B'lore ", "Bangalore ");
        cityAbbreviations.put("B'lore,", "Bangalore,");
        cityAbbreviations.put("Mum", "Mumbai");
        cityAbbreviations.put("Mum ", "Mumbai ");
        cityAbbreviations.put("Mum,", "Mumbai,");
        cityAbbreviations.put("Del", "Delhi");
        cityAbbreviations.put("Del ", "Delhi ");
        cityAbbreviations.put("Del,", "Delhi,");
        cityAbbreviations.put("Chennai", "Chennai");
        cityAbbreviations.put("Madras", "Chennai");
        cityAbbreviations.put("Cal", "Kolkata");
        cityAbbreviations.put("Cal ", "Kolkata ");
        cityAbbreviations.put("Cal,", "Kolkata,");
        
        // Expand city abbreviations
        for (java.util.Map.Entry<String, String> entry : cityAbbreviations.entrySet()) {
            if (address.contains(entry.getKey())) {
                String expanded = address.replace(entry.getKey(), entry.getValue());
                if (!variations.contains(expanded)) {
                    variations.add(expanded);
                }
            }
        }
        
        // Common state abbreviations mapping (India)
        java.util.Map<String, String> stateAbbreviations = new java.util.HashMap<>();
        stateAbbreviations.put("AP", "Andhra Pradesh");
        stateAbbreviations.put("UP", "Uttar Pradesh");
        stateAbbreviations.put("MP", "Madhya Pradesh");
        stateAbbreviations.put("TN", "Tamil Nadu");
        stateAbbreviations.put("MH", "Maharashtra");
        stateAbbreviations.put("KA", "Karnataka");
        stateAbbreviations.put("GJ", "Gujarat");
        stateAbbreviations.put("RJ", "Rajasthan");
        stateAbbreviations.put("WB", "West Bengal");
        stateAbbreviations.put("OD", "Odisha");
        stateAbbreviations.put("PB", "Punjab");
        stateAbbreviations.put("HR", "Haryana");
        stateAbbreviations.put("AS", "Assam");
        stateAbbreviations.put("BR", "Bihar");
        stateAbbreviations.put("JK", "Jammu and Kashmir");
        stateAbbreviations.put("JH", "Jharkhand");
        stateAbbreviations.put("CT", "Chhattisgarh");
        stateAbbreviations.put("UT", "Uttarakhand");
        stateAbbreviations.put("HP", "Himachal Pradesh");
        stateAbbreviations.put("TR", "Tripura");
        stateAbbreviations.put("ML", "Meghalaya");
        stateAbbreviations.put("MN", "Manipur");
        stateAbbreviations.put("NL", "Nagaland");
        stateAbbreviations.put("GA", "Goa");
        stateAbbreviations.put("AR", "Arunachal Pradesh");
        stateAbbreviations.put("MZ", "Mizoram");
        stateAbbreviations.put("SK", "Sikkim");
        stateAbbreviations.put("DL", "Delhi");
        stateAbbreviations.put("PY", "Puducherry");
        stateAbbreviations.put("CH", "Chandigarh");
        stateAbbreviations.put("AN", "Andaman and Nicobar Islands");
        stateAbbreviations.put("DN", "Dadra and Nagar Haveli");
        stateAbbreviations.put("DD", "Daman and Diu");
        stateAbbreviations.put("LD", "Lakshadweep");
        
        // Try expanding state abbreviations
        for (java.util.Map.Entry<String, String> entry : stateAbbreviations.entrySet()) {
            String abbrev = entry.getKey();
            String fullName = entry.getValue();
            
            // Replace "AP" with "Andhra Pradesh" etc.
            if (address.contains(", " + abbrev + ",") || address.endsWith(", " + abbrev)) {
                String expanded = address.replace(", " + abbrev + ",", ", " + fullName + ",")
                                         .replace(", " + abbrev, ", " + fullName);
                if (!variations.contains(expanded)) {
                    variations.add(expanded);
                }
            }
            
            // Also try with space instead of comma
            if (address.contains(" " + abbrev + " ") || address.endsWith(" " + abbrev)) {
                String expanded = address.replace(" " + abbrev + " ", " " + fullName + " ")
                                         .replace(" " + abbrev, " " + fullName);
                if (!variations.contains(expanded)) {
                    variations.add(expanded);
                }
            }
        }
        
        // Try variations without country
        if (address.contains(", India")) {
            String withoutCountry = address.replace(", India", "").trim();
            if (!variations.contains(withoutCountry)) {
                variations.add(withoutCountry);
            }
        }
        
        // Try variations without state (if it has state)
        String[] parts = address.split(",");
        if (parts.length >= 2) {
            // Try just the first part (city/town name)
            String cityOnly = parts[0].trim();
            if (!cityOnly.isEmpty() && !variations.contains(cityOnly)) {
                variations.add(cityOnly);
            }
            
            // Try city + country (skip state)
            if (parts.length >= 3) {
                String cityAndCountry = parts[0].trim() + ", " + parts[parts.length - 1].trim();
                if (!variations.contains(cityAndCountry)) {
                    variations.add(cityAndCountry);
                }
            }
        }
        
        // Try with "India" explicitly added if not present
        if (!address.contains("India") && !address.contains("IND")) {
            String withIndia = address + ", India";
            if (!variations.contains(withIndia)) {
                variations.add(withIndia);
            }
        }
        
        // Special handling for power plants, thermal plants, etc.
        // "Vizag Thermal Power Plant" -> "Visakhapatnam Thermal Power Plant, Andhra Pradesh, India"
        if (address.toLowerCase().contains("thermal") || address.toLowerCase().contains("power plant")) {
            // Try with full city name if abbreviated
            String expanded = address;
            if (address.contains("Vizag")) {
                expanded = address.replace("Vizag", "Visakhapatnam");
            }
            if (!expanded.contains("Andhra Pradesh") && !expanded.contains("AP")) {
                String withState = expanded + ", Andhra Pradesh, India";
                if (!variations.contains(withState)) {
                    variations.add(withState);
                }
            }
        }
        
        // For locations in Andhra Pradesh, try adding "Andhra Pradesh" explicitly
        if (address.contains("Vizag") || address.contains("Visakhapatnam") || 
            address.contains("Vizianagaram") || address.contains("Vijayawada") ||
            address.contains("Guntur") || address.contains("Tirupati")) {
            if (!address.contains("Andhra Pradesh") && !address.contains("AP")) {
                String withAP = address + ", Andhra Pradesh, India";
                if (!variations.contains(withAP)) {
                    variations.add(withAP);
                }
            }
        }
        
        return variations;
    }
    
    /**
     * @deprecated This method is no longer used. Replaced by simplified geocodeAddressDirect()
     * Kept for reference but will be removed in future versions.
     */
    @Deprecated
    private String[] tryGeocodeAddress(String address) {
        try {
            // OpenRouteService Geocoding API endpoint (GET request)
            // Using /geocode/search endpoint (v2 API)
            String geocodeUrl = "https://api.openrouteservice.org/geocode/search";
            
            HttpHeaders headers = new HttpHeaders();
            // OpenRouteService requires API key in Authorization header with Bearer prefix
            String authHeader = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
            headers.set("Authorization", authHeader);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            
            // Build URL with query parameters (text parameter for search)
            // Global geocoding - no country restriction, but prioritize India if address suggests it
            String encodedAddress = java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);
            String url = geocodeUrl + "?text=" + encodedAddress + 
                        "&size=10"; // Get up to 10 results to find the best match
            
            // Optionally add focus point for India if address suggests Indian location
            boolean addressSuggestsIndia = address.toLowerCase().contains("india") || 
                                          address.toLowerCase().contains("indian") ||
                                          address.toLowerCase().contains(", in");
            if (addressSuggestsIndia) {
                url += "&focus.point.lat=20.5937&focus.point.lon=78.9629";
            }
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.debug("Trying to geocode: {}", address);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                // Check if it's a 404 - might be endpoint issue
                if (response.getStatusCode().value() == 404) {
                    throw new RuntimeException("Geocoding endpoint not found. Please verify the OpenRouteService API endpoint is correct.");
                }
                return null; // Try next variation
            }
            
            JsonNode root = objectMapper.readTree(response.getBody());
            
            // Check for errors in response
            if (root.has("error")) {
                return null; // Try next variation
            }
            
            JsonNode features = root.path("features");
            
            if (!features.isArray() || features.size() == 0) {
                return null; // Try next variation
            }
            
            // Find the best matching result from all features
            // Works globally - no country restrictions, but prioritizes:
            // 1. Higher confidence/importance scores
            // 2. Exact address matches
            // 3. India results (if address suggests India)
            // Note: addressSuggestsIndia is already declared above (line 521)
            String[] bestCoords = null;
            double bestScore = -1;
            String bestLabel = null;
            
            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                JsonNode coordinates = geometry.path("coordinates");
                
                if (!coordinates.isArray() || coordinates.size() < 2) {
                    continue;
                }
                
                // OpenRouteService returns [longitude, latitude]
                double longitude = coordinates.get(0).asDouble();
                double latitude = coordinates.get(1).asDouble();
                
                // Validate coordinates are within valid range (global)
                if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                    continue;
                }
                
                // Calculate score based on confidence and relevance
                double score = 0.0;
                
                // Check if result has confidence/importance score
                JsonNode properties = feature.path("properties");
                String label = properties.path("label").asText("");
                String name = properties.path("name").asText("");
                
                // Big bonus if the label/name contains the original address keywords
                String addressLower = address.toLowerCase();
                if (label.toLowerCase().contains(addressLower) || name.toLowerCase().contains(addressLower)) {
                    score += 200; // Very high priority for exact matches
                }
                
                if (properties.has("confidence")) {
                    score += properties.path("confidence").asDouble(0.0) * 5;
                }
                if (properties.has("importance")) {
                    score += properties.path("importance").asDouble(0.0) * 20; // Weight importance more
                }
                
                // Prefer results with country code "IN" if address suggests India
                String countryCode = properties.path("country_code").asText("");
                if (addressSuggestsIndia && "IN".equals(countryCode)) {
                    score += 150; // Big bonus for India when address suggests India
                } else if ("IN".equals(countryCode)) {
                    score += 50; // Smaller bonus for India even if not explicitly mentioned
                }
                
                // Prefer results in Andhra Pradesh (for AP cities)
                if (properties.has("region") && "Andhra Pradesh".equalsIgnoreCase(properties.path("region").asText(""))) {
                    score += 50; // Bonus for AP
                }
                
                if (score > bestScore) {
                    bestScore = score;
                    bestCoords = new String[]{String.valueOf(longitude), String.valueOf(latitude)};
                    bestLabel = label;
                }
            }
            
            if (bestCoords == null) {
                log.warn("No valid coordinates found for '{}'", address);
                return null;
            }
            
            double longitude = Double.parseDouble(bestCoords[0]);
            double latitude = Double.parseDouble(bestCoords[1]);
            
            log.info("Geocoded '{}' to coordinates: longitude={}, latitude={} (score: {}, label: '{}')", 
                address, longitude, latitude, String.format("%.2f", bestScore), bestLabel != null ? bestLabel : "N/A");
            return bestCoords;
            
        } catch (Exception ex) {
            // Log but don't throw - let caller try next variation
            log.debug("Error geocoding address '{}': {}", address, ex.getMessage());
            return null;
        }
    }
    
    /**
     * Get address autocomplete suggestions using OpenRouteService Geocoding API
     * This method works globally for all locations, not restricted to any country
     * Returns 5-10 best suggestions as user types
     */
    public java.util.List<Map<String, Object>> getAddressSuggestions(String query) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouteService API key is not configured.");
        }
        
        if (query == null || query.trim().isEmpty() || query.trim().length() < 2) {
            return java.util.Collections.emptyList();
        }
        
        try {
            // Using /geocode/autocomplete endpoint for better autocomplete results
            String geocodeUrl = "https://api.openrouteservice.org/geocode/autocomplete";
            
            HttpHeaders headers = new HttpHeaders();
            // OpenRouteService requires API key in Authorization header with Bearer prefix
            String authHeader = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
            headers.set("Authorization", authHeader);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            
            String encodedQuery = java.net.URLEncoder.encode(query.trim(), java.nio.charset.StandardCharsets.UTF_8);
            // CRITICAL: Force India-only results to prevent wrong foreign matches
            // This ensures autocomplete only returns Indian locations
            String url = geocodeUrl + "?text=" + encodedQuery + 
                        "&boundary.country=IN" +  // RESTRICT TO INDIA ONLY - prevents foreign matches
                        "&size=10";              // Get up to 10 suggestions
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.debug("Fetching autocomplete suggestions for query: '{}'", query);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("OpenRouteService autocomplete returned status: {}", response.getStatusCode());
                return java.util.Collections.emptyList();
            }
            
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                log.warn("OpenRouteService autocomplete error: {}", errorMsg);
                return java.util.Collections.emptyList();
            }
            
            JsonNode features = root.path("features");
            if (!features.isArray() || features.size() == 0) {
                return java.util.Collections.emptyList();
            }
            
            java.util.List<Map<String, Object>> suggestions = new java.util.ArrayList<>();
            for (JsonNode feature : features) {
                Map<String, Object> suggestion = new HashMap<>();
                
                // Get the display name (formatted address)
                JsonNode properties = feature.path("properties");
                String name = properties.path("name").asText("");
                String label = properties.path("label").asText(name);
                String countryCode = properties.path("country_code").asText("");
                
                // CRITICAL: Only include India results (double-check even with boundary restriction)
                if (!"IN".equals(countryCode) && !countryCode.isEmpty()) {
                    log.warn("Skipping non-India suggestion: {} (country: {})", label, countryCode);
                    continue; // Skip non-India results
                }
                
                // Use label as primary display, fallback to name
                suggestion.put("label", label);
                suggestion.put("name", name);
                
                // Get coordinates (OpenRouteService returns [longitude, latitude])
                JsonNode geometry = feature.path("geometry");
                JsonNode coordinates = geometry.path("coordinates");
                if (coordinates.isArray() && coordinates.size() >= 2) {
                    double lon = coordinates.get(0).asDouble();
                    double lat = coordinates.get(1).asDouble();
                    
                    // Validate coordinates are within India boundaries
                    // India: Longitude 68°E-97°E (68 to 97), Latitude 6°N-37°N (6 to 37)
                    if (lon >= 68 && lon <= 97 && lat >= 6 && lat <= 37) {
                        suggestion.put("longitude", lon);
                        suggestion.put("latitude", lat);
                    } else {
                        log.warn("Skipping suggestion with coordinates outside India: {} ([{}, {}])", 
                            label, lon, lat);
                        continue; // Skip if coordinates are outside India
                    }
                }
                
                // Get additional details for better display
                String country = properties.path("country").asText("");
                String region = properties.path("region").asText("");
                String locality = properties.path("locality").asText("");
                String county = properties.path("county").asText("");
                
                suggestion.put("country", country);
                suggestion.put("region", region);
                suggestion.put("locality", locality);
                suggestion.put("county", county);
                suggestion.put("countryCode", "IN"); // Force India
                
                suggestions.add(suggestion);
            }
            
            log.debug("Returning {} autocomplete suggestions for query '{}'", suggestions.size(), query);
            return suggestions;
            
        } catch (Exception ex) {
            log.error("Error getting address suggestions for '{}': {}", query, ex.getMessage(), ex);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * Calculate distance using OpenRouteService Directions API with coordinates
     * SIMPLIFIED: Direct API call, no complex validation
     * Returns accurate driving distance and duration
     */
    private DistanceMatrixResult calculateDistanceFromCoordinates(String[] sourceCoords, String[] destCoords, String source, String destination) {
        try {
            // Use Directions API for accurate route-based distance calculation
            String directionsUrl = "https://api.openrouteservice.org/v2/directions/driving-car";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // OpenRouteService requires API key in Authorization header with Bearer prefix
            String authHeader = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
            headers.set("Authorization", authHeader);
            
            // Parse and validate coordinates
            double sourceLon = Double.parseDouble(sourceCoords[0]);
            double sourceLat = Double.parseDouble(sourceCoords[1]);
            double destLon = Double.parseDouble(destCoords[0]);
            double destLat = Double.parseDouble(destCoords[1]);
            
            // Validate coordinates are reasonable
            if (sourceLon < -180 || sourceLon > 180 || sourceLat < -90 || sourceLat > 90 ||
                destLon < -180 || destLon > 180 || destLat < -90 || destLat > 90) {
                throw new RuntimeException("Invalid coordinates: source=[" + sourceLon + "," + sourceLat + 
                    "], destination=[" + destLon + "," + destLat + "]");
            }
            
            // Build request body - OpenRouteService expects [[lon, lat], [lon, lat]]
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("coordinates", new double[][]{
                {sourceLon, sourceLat},  // [longitude, latitude]
                {destLon, destLat}
            });
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Calling OpenRouteService Directions API for source='{}' ({}, {}), destination='{}' ({}, {})", 
                source, sourceCoords[0], sourceCoords[1], destination, destCoords[0], destCoords[1]);
            ResponseEntity<String> response = restTemplate.postForEntity(directionsUrl, request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                String errorBody = response.getBody();
                log.error("OpenRouteService Directions API returned status: {}, Body: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("OpenRouteService Directions API returned status: " + response.getStatusCode());
            }
            
            JsonNode root = objectMapper.readTree(response.getBody());
            
            // Check for errors in response
            if (root.has("error")) {
                String errorMessage = root.path("error").path("message").asText("Unknown error");
                log.error("OpenRouteService Directions API error: {}", errorMessage);
                throw new RuntimeException("OpenRouteService Directions API error: " + errorMessage);
            }
            
            // Extract route information from Directions API response
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.size() == 0) {
                throw new RuntimeException("OpenRouteService Directions API returned no routes");
            }
            
            JsonNode route = routes.get(0);
            JsonNode summary = route.path("summary");
            
            // Distance is in meters, duration is in seconds
            double distanceMeters = summary.path("distance").asDouble();
            double durationSeconds = summary.path("duration").asDouble();
            
            // Validate the results
            if (distanceMeters <= 0 || durationSeconds <= 0) {
                log.error("Invalid distance or duration from Directions API: distance={}m, duration={}s", distanceMeters, durationSeconds);
                throw new RuntimeException("OpenRouteService Directions API returned invalid distance or duration");
            }
            
            double distanceKm = distanceMeters / 1000.0;
            
            // Format duration text
            long hours = (long) (durationSeconds / 3600);
            long minutes = (long) ((durationSeconds % 3600) / 60);
            String durationText;
            if (hours > 0) {
                durationText = String.format("%d hour%s %d min%s", hours, hours > 1 ? "s" : "", minutes, minutes != 1 ? "s" : "");
            } else {
                durationText = String.format("%d min%s", minutes, minutes != 1 ? "s" : "");
            }
            
            DistanceMatrixResult result = new DistanceMatrixResult();
            result.setDistanceKm(distanceKm);
            result.setDurationSeconds((long) durationSeconds);
            result.setDurationText(durationText);
            
            log.info("Distance calculation successful: {} km ({} meters), duration: {} ({} seconds) for route: {} -> {}", 
                distanceKm, (long)distanceMeters, durationText, (long)durationSeconds, source, destination);
            log.info("Coordinates used - Source: [{}, {}], Destination: [{}, {}]", 
                sourceLon, sourceLat, destLon, destLat);
            return result;
            
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error calling OpenRouteService Directions API: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to calculate distance using OpenRouteService Directions API: " + ex.getMessage(), ex);
        }
    }

    /**
     * Calculate straight-line distance using Haversine formula (for validation)
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    /**
     * Simple DTO to hold distance matrix result.
     */
    @Data
    @AllArgsConstructor
    public static class DistanceMatrixResult {
        private double distanceKm;
        private long durationSeconds;
        private String durationText;

        public DistanceMatrixResult() {
        }
    }
}


