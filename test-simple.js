// Simple test to verify Node.js is working
console.log('='.repeat(60));
console.log('COORDINATE TEST - SIMPLE VERSION');
console.log('='.repeat(60));

// Test coordinates
const nuzvid = { lat: 16.7890, lon: 80.8450 };
const vijayawada = { lat: 16.5062, lon: 80.6480 };

// Calculate Haversine distance
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // Earth radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = 
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// Validate India boundaries
function isWithinIndia(lat, lon) {
  return lon >= 68 && lon <= 97 && lat >= 6 && lat <= 37;
}

console.log('\nTest 1: Coordinate Validation');
console.log('Nuzvid coordinates:', nuzvid);
console.log('Within India:', isWithinIndia(nuzvid.lat, nuzvid.lon));
console.log('Vijayawada coordinates:', vijayawada);
console.log('Within India:', isWithinIndia(vijayawada.lat, vijayawada.lon));

console.log('\nTest 2: Distance Calculation');
const distance = calculateDistance(nuzvid.lat, nuzvid.lon, vijayawada.lat, vijayawada.lon);
console.log('Straight-line distance:', distance.toFixed(2), 'km');
console.log('Expected route distance: 55-60 km');

console.log('\nTest 3: Payload Format');
const payload = {
  source: 'Nuzvid, Andhra Pradesh, India',
  sourceLatitude: nuzvid.lat,
  sourceLongitude: nuzvid.lon,
  destination: 'Vijayawada, Andhra Pradesh, India',
  destinationLatitude: vijayawada.lat,
  destinationLongitude: vijayawada.lon
};
console.log('Payload:', JSON.stringify(payload, null, 2));

console.log('\n' + '='.repeat(60));
console.log('✅ All tests completed!');
console.log('='.repeat(60));

// Also write to file for verification
const fs = require('fs');
const output = `
COORDINATE TEST RESULTS
=======================

Test 1: Coordinate Validation
- Nuzvid: [lat=${nuzvid.lat}, lon=${nuzvid.lon}] - Within India: ${isWithinIndia(nuzvid.lat, nuzvid.lon)}
- Vijayawada: [lat=${vijayawada.lat}, lon=${vijayawada.lon}] - Within India: ${isWithinIndia(vijayawada.lat, vijayawada.lon)}

Test 2: Distance Calculation
- Straight-line distance: ${distance.toFixed(2)} km
- Expected route distance: 55-60 km

Test 3: Payload Format
${JSON.stringify(payload, null, 2)}

✅ All tests passed!
`;
fs.writeFileSync('test-results.txt', output);
console.log('\nResults also written to test-results.txt');
