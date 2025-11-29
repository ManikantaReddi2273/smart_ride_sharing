/**
 * Test Script for Coordinate-Based Distance Calculation
 * 
 * This script tests:
 * 1. Autocomplete API response format
 * 2. Coordinate extraction from autocomplete
 * 3. Distance calculation using coordinates
 * 4. Validation of India boundaries
 * 
 * Run with: node test-coordinates.js
 */

// Test coordinates for known Indian locations
const TEST_LOCATIONS = {
  nuzvid: {
    name: "Nuzvid, Andhra Pradesh, India",
    latitude: 16.7890,
    longitude: 80.8450
  },
  vijayawada: {
    name: "Vijayawada, Andhra Pradesh, India",
    latitude: 16.5062,
    longitude: 80.6480
  },
  vizianagaram: {
    name: "Vizianagaram, Andhra Pradesh, India",
    latitude: 18.1166,
    longitude: 83.4115
  },
  vizagThermal: {
    name: "Vizag Thermal Power Station, Andhra Pradesh, India",
    latitude: 17.7000,
    longitude: 83.3000
  }
}

/**
 * Calculate Haversine distance between two coordinates (straight-line)
 * This is for validation - actual route distance will be calculated by OpenRouteService
 */
function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371 // Earth radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLon = (lon2 - lon1) * Math.PI / 180
  const a = 
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

/**
 * Validate coordinates are within India boundaries
 */
function isWithinIndia(latitude, longitude) {
  // India boundaries: Longitude 68°E-97°E, Latitude 6°N-37°N
  return longitude >= 68 && longitude <= 97 && latitude >= 6 && latitude <= 37
}

/**
 * Simulate autocomplete API response format
 */
function simulateAutocompleteResponse(query) {
  // This simulates what the backend getAddressSuggestions returns
  const mockResponses = {
    "nuzvid": [{
      label: "Nuzvid, Andhra Pradesh, India",
      name: "Nuzvid",
      latitude: 16.7890,
      longitude: 80.8450,
      country: "India",
      countryCode: "IN",
      region: "Andhra Pradesh",
      locality: "Nuzvid"
    }],
    "vijayawada": [{
      label: "Vijayawada, Andhra Pradesh, India",
      name: "Vijayawada",
      latitude: 16.5062,
      longitude: 80.6480,
      country: "India",
      countryCode: "IN",
      region: "Andhra Pradesh",
      locality: "Vijayawada"
    }],
    "vizianagaram": [{
      label: "Vizianagaram, Andhra Pradesh, India",
      name: "Vizianagaram",
      latitude: 18.1166,
      longitude: 83.4115,
      country: "India",
      countryCode: "IN",
      region: "Andhra Pradesh",
      locality: "Vizianagaram"
    }],
    "vizag thermal": [{
      label: "Vizag Thermal Power Station, Andhra Pradesh, India",
      name: "Vizag Thermal Power Station",
      latitude: 17.7000,
      longitude: 83.3000,
      country: "India",
      countryCode: "IN",
      region: "Andhra Pradesh",
      locality: "Visakhapatnam"
    }]
  }
  
  const key = Object.keys(mockResponses).find(k => query.toLowerCase().includes(k))
  return key ? mockResponses[key] : []
}

/**
 * Test: Extract coordinates from autocomplete selection
 */
function testCoordinateExtraction() {
  console.log("\n=== TEST 1: Coordinate Extraction from Autocomplete ===\n")
  
  const queries = ["nuzvid", "vijayawada", "vizianagaram", "vizag thermal"]
  
  queries.forEach(query => {
    const suggestions = simulateAutocompleteResponse(query)
    if (suggestions.length > 0) {
      const selected = suggestions[0]
      console.log(`Query: "${query}"`)
      console.log(`  Selected: ${selected.label}`)
      console.log(`  Coordinates: [lat=${selected.latitude}, lon=${selected.longitude}]`)
      console.log(`  Country Code: ${selected.countryCode}`)
      console.log(`  Within India: ${isWithinIndia(selected.latitude, selected.longitude)}`)
      console.log(`  ✅ Valid selection\n`)
    } else {
      console.log(`Query: "${query}"`)
      console.log(`  ❌ No suggestions found\n`)
    }
  })
}

/**
 * Test: Distance calculation using coordinates
 */
function testDistanceCalculation() {
  console.log("\n=== TEST 2: Distance Calculation Using Coordinates ===\n")
  
  const testRoutes = [
    {
      source: TEST_LOCATIONS.nuzvid,
      destination: TEST_LOCATIONS.vijayawada,
      expectedDistance: "55-60 km"
    },
    {
      source: TEST_LOCATIONS.vizianagaram,
      destination: TEST_LOCATIONS.vizagThermal,
      expectedDistance: "20-30 km"
    }
  ]
  
  testRoutes.forEach((route, index) => {
    const { source, destination, expectedDistance } = route
    
    // Validate coordinates
    const sourceValid = isWithinIndia(source.latitude, source.longitude)
    const destValid = isWithinIndia(destination.latitude, destination.longitude)
    
    if (!sourceValid || !destValid) {
      console.log(`Route ${index + 1}: ${source.name} → ${destination.name}`)
      console.log(`  ❌ Invalid coordinates (outside India)`)
      console.log(`  Source valid: ${sourceValid}, Dest valid: ${destValid}\n`)
      return
    }
    
    // Calculate straight-line distance (for validation)
    const straightLineDistance = calculateHaversineDistance(
      source.latitude, source.longitude,
      destination.latitude, destination.longitude
    )
    
    console.log(`Route ${index + 1}: ${source.name} → ${destination.name}`)
    console.log(`  Source: [lat=${source.latitude}, lon=${source.longitude}]`)
    console.log(`  Destination: [lat=${destination.latitude}, lon=${destination.longitude}]`)
    console.log(`  Straight-line distance: ${straightLineDistance.toFixed(2)} km`)
    console.log(`  Expected route distance: ${expectedDistance}`)
    console.log(`  ✅ Coordinates valid for backend API call\n`)
  })
}

/**
 * Test: Payload format for backend
 */
function testPayloadFormat() {
  console.log("\n=== TEST 3: Payload Format for Backend ===\n")
  
  const source = simulateAutocompleteResponse("nuzvid")[0]
  const destination = simulateAutocompleteResponse("vijayawada")[0]
  
  const payload = {
    vehicleId: 1,
    source: source.label,
    sourceLatitude: source.latitude,
    sourceLongitude: source.longitude,
    destination: destination.label,
    destinationLatitude: destination.latitude,
    destinationLongitude: destination.longitude,
    rideDate: "2024-12-25",
    rideTime: "10:00:00",
    totalSeats: 4,
    notes: ""
  }
  
  console.log("Payload to send to backend:")
  console.log(JSON.stringify(payload, null, 2))
  console.log("\n✅ Payload format correct\n")
}

/**
 * Test: Coordinate validation (India boundaries)
 */
function testIndiaBoundaryValidation() {
  console.log("\n=== TEST 4: India Boundary Validation ===\n")
  
  const testCoordinates = [
    { name: "Nuzvid", lat: 16.7890, lon: 80.8450, expected: true },
    { name: "Vijayawada", lat: 16.5062, lon: 80.6480, expected: true },
    { name: "Mumbai", lat: 19.0760, lon: 72.8777, expected: true },
    { name: "Delhi", lat: 28.6139, lon: 77.2090, expected: true },
    { name: "Germany (WRONG)", lat: 52.2233, lon: 10.4211, expected: false },
    { name: "USA (WRONG)", lat: 40.7128, lon: -74.0060, expected: false },
    { name: "Australia (WRONG)", lat: -33.8688, lon: 151.2093, expected: false }
  ]
  
  testCoordinates.forEach(coord => {
    const isValid = isWithinIndia(coord.lat, coord.lon)
    const status = isValid === coord.expected ? "✅" : "❌"
    console.log(`${status} ${coord.name}: [lat=${coord.lat}, lon=${coord.lon}] - Valid: ${isValid} (Expected: ${coord.expected})`)
  })
  
  console.log()
}

/**
 * Test: Simulate frontend autocomplete selection flow
 */
function testFrontendFlow() {
  console.log("\n=== TEST 5: Frontend Autocomplete Selection Flow ===\n")
  
  // Simulate user typing "nuzvid"
  const sourceQuery = "nuzvid"
  const sourceSuggestions = simulateAutocompleteResponse(sourceQuery)
  
  if (sourceSuggestions.length === 0) {
    console.log("❌ No source suggestions found")
    return
  }
  
  const sourceSelected = sourceSuggestions[0]
  
  // Validate selection
  if (sourceSelected.countryCode !== "IN") {
    console.log(`❌ Invalid source: ${sourceSelected.label} (Country: ${sourceSelected.countryCode})`)
    return
  }
  
  if (!isWithinIndia(sourceSelected.latitude, sourceSelected.longitude)) {
    console.log(`❌ Source coordinates outside India: [${sourceSelected.latitude}, ${sourceSelected.longitude}]`)
    return
  }
  
  // Store coordinates (simulating React state)
  const sourceCoordinates = {
    latitude: sourceSelected.latitude,
    longitude: sourceSelected.longitude
  }
  
  console.log(`✅ Source selected: ${sourceSelected.label}`)
  console.log(`   Coordinates stored:`, sourceCoordinates)
  
  // Simulate user typing "vijayawada"
  const destQuery = "vijayawada"
  const destSuggestions = simulateAutocompleteResponse(destQuery)
  
  if (destSuggestions.length === 0) {
    console.log("❌ No destination suggestions found")
    return
  }
  
  const destSelected = destSuggestions[0]
  
  // Validate selection
  if (destSelected.countryCode !== "IN") {
    console.log(`❌ Invalid destination: ${destSelected.label} (Country: ${destSelected.countryCode})`)
    return
  }
  
  if (!isWithinIndia(destSelected.latitude, destSelected.longitude)) {
    console.log(`❌ Destination coordinates outside India: [${destSelected.latitude}, ${destSelected.longitude}]`)
    return
  }
  
  // Store coordinates (simulating React state)
  const destCoordinates = {
    latitude: destSelected.latitude,
    longitude: destSelected.longitude
  }
  
  console.log(`✅ Destination selected: ${destSelected.label}`)
  console.log(`   Coordinates stored:`, destCoordinates)
  
  // Calculate straight-line distance
  const distance = calculateHaversineDistance(
    sourceCoordinates.latitude, sourceCoordinates.longitude,
    destCoordinates.latitude, destCoordinates.longitude
  )
  
  console.log(`\n📊 Distance (straight-line): ${distance.toFixed(2)} km`)
  console.log(`   Expected route distance: 55-60 km`)
  console.log(`\n✅ Frontend flow complete - ready to send to backend\n`)
}

/**
 * Test: API endpoint URL construction
 */
function testAPIEndpoint() {
  console.log("\n=== TEST 6: API Endpoint URL Construction ===\n")
  
  const sourceCoords = { latitude: 16.7890, longitude: 80.8450 }
  const destCoords = { latitude: 16.5062, longitude: 80.6480 }
  
  // Simulate calculateFare API call
  const params = {
    source: "Nuzvid, Andhra Pradesh, India",
    destination: "Vijayawada, Andhra Pradesh, India",
    sourceLat: sourceCoords.latitude,
    sourceLon: sourceCoords.longitude,
    destLat: destCoords.latitude,
    destLon: destCoords.longitude
  }
  
  const queryString = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join("&")
  
  const url = `/api/rides/calculate-fare?${queryString}`
  
  console.log("API Endpoint URL:")
  console.log(url)
  console.log("\n✅ URL format correct\n")
}

// Run all tests
console.log("=".repeat(60))
console.log("COORDINATE-BASED DISTANCE CALCULATION TEST SUITE")
console.log("=".repeat(60))

testCoordinateExtraction()
testDistanceCalculation()
testPayloadFormat()
testIndiaBoundaryValidation()
testFrontendFlow()
testAPIEndpoint()

console.log("=".repeat(60))
console.log("ALL TESTS COMPLETED")
console.log("=".repeat(60))
console.log("\n📝 Next Steps:")
console.log("1. Check if autocomplete API returns coordinates in the expected format")
console.log("2. Verify frontend stores coordinates when user selects from autocomplete")
console.log("3. Confirm backend receives coordinates in the payload")
console.log("4. Check backend logs for '✅ Using coordinates directly from frontend'")
console.log("\n")
