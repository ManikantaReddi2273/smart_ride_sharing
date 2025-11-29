# PowerShell Test Script for Coordinate-Based Distance Calculation
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "COORDINATE-BASED DISTANCE CALCULATION TEST" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Test coordinates
$nuzvid = @{
    name = "Nuzvid, Andhra Pradesh, India"
    latitude = 16.7890
    longitude = 80.8450
}

$vijayawada = @{
    name = "Vijayawada, Andhra Pradesh, India"
    latitude = 16.5062
    longitude = 80.6480
}

# Function to validate India boundaries
function Test-IndiaBoundary {
    param($latitude, $longitude)
    return ($longitude -ge 68 -and $longitude -le 97 -and $latitude -ge 6 -and $latitude -le 37)
}

# Function to calculate Haversine distance
function Get-Distance {
    param($lat1, $lon1, $lat2, $lon2)
    $R = 6371  # Earth radius in km
    $dLat = ($lat2 - $lat1) * [Math]::PI / 180
    $dLon = ($lon2 - $lon1) * [Math]::PI / 180
    $a = [Math]::Sin($dLat / 2) * [Math]::Sin($dLat / 2) +
         [Math]::Cos($lat1 * [Math]::PI / 180) * [Math]::Cos($lat2 * [Math]::PI / 180) *
         [Math]::Sin($dLon / 2) * [Math]::Sin($dLon / 2)
    $c = 2 * [Math]::Atan2([Math]::Sqrt($a), [Math]::Sqrt(1 - $a))
    return $R * $c
}

Write-Host "Test 1: Coordinate Validation" -ForegroundColor Yellow
Write-Host "------------------------------"
Write-Host "Nuzvid:" -ForegroundColor White
Write-Host "  Coordinates: [lat=$($nuzvid.latitude), lon=$($nuzvid.longitude)]"
$nuzvidValid = Test-IndiaBoundary -latitude $nuzvid.latitude -longitude $nuzvid.longitude
Write-Host "  Within India: $nuzvidValid" -ForegroundColor $(if ($nuzvidValid) { "Green" } else { "Red" })
Write-Host ""
Write-Host "Vijayawada:" -ForegroundColor White
Write-Host "  Coordinates: [lat=$($vijayawada.latitude), lon=$($vijayawada.longitude)]"
$vijayawadaValid = Test-IndiaBoundary -latitude $vijayawada.latitude -longitude $vijayawada.longitude
Write-Host "  Within India: $vijayawadaValid" -ForegroundColor $(if ($vijayawadaValid) { "Green" } else { "Red" })
Write-Host ""

Write-Host "Test 2: Distance Calculation" -ForegroundColor Yellow
Write-Host "------------------------------"
$distance = Get-Distance -lat1 $nuzvid.latitude -lon1 $nuzvid.longitude -lat2 $vijayawada.latitude -lon2 $vijayawada.longitude
Write-Host "Straight-line distance: $([Math]::Round($distance, 2)) km" -ForegroundColor Cyan
Write-Host "Expected route distance: 55-60 km" -ForegroundColor Cyan
Write-Host ""

Write-Host "Test 3: Payload Format" -ForegroundColor Yellow
Write-Host "------------------------------"
$payload = @{
    source = $nuzvid.name
    sourceLatitude = $nuzvid.latitude
    sourceLongitude = $nuzvid.longitude
    destination = $vijayawada.name
    destinationLatitude = $vijayawada.latitude
    destinationLongitude = $vijayawada.longitude
    vehicleId = 1
    rideDate = "2024-12-25"
    rideTime = "10:00:00"
    totalSeats = 4
}
Write-Host "Payload to send to backend:" -ForegroundColor White
$payload | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor Cyan
Write-Host ""

Write-Host "Test 4: API Endpoint URL" -ForegroundColor Yellow
Write-Host "------------------------------"
$params = @{
    source = $nuzvid.name
    destination = $vijayawada.name
    sourceLat = $nuzvid.latitude
    sourceLon = $nuzvid.longitude
    destLat = $vijayawada.latitude
    destLon = $vijayawada.longitude
}
$queryString = ($params.GetEnumerator() | ForEach-Object { "$($_.Key)=$([System.Web.HttpUtility]::UrlEncode($_.Value))" }) -join "&"
$url = "http://localhost:8082/api/rides/calculate-fare?$queryString"
Write-Host "API URL:" -ForegroundColor White
Write-Host $url -ForegroundColor Cyan
Write-Host ""

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "✅ ALL TESTS COMPLETED!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Open test-autocomplete-api.html in browser to test actual API" -ForegroundColor White
Write-Host "2. Check backend logs for '✅ Using coordinates directly from frontend'" -ForegroundColor White
Write-Host "3. Verify frontend stores coordinates when selecting from autocomplete" -ForegroundColor White
Write-Host ""
