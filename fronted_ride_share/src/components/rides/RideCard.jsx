import PropTypes from 'prop-types'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
  Divider,
} from '@mui/material'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import BookmarkAddRoundedIcon from '@mui/icons-material/BookmarkAddRounded'
import PersonRoundedIcon from '@mui/icons-material/PersonRounded'
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded'
import AttachMoneyRoundedIcon from '@mui/icons-material/AttachMoneyRounded'
import StraightenRoundedIcon from '@mui/icons-material/StraightenRounded'

const formatDate = (dateString) => {
  if (!dateString) return 'N/A'
  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString
    if (isNaN(date.getTime())) return 'N/A'
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return 'N/A'
  }
}

const formatTime = (timeString) => {
  if (!timeString) return 'N/A'
  try {
    const timeParts = timeString.split(':')
    if (timeParts.length < 2) return 'N/A'
    const hour = parseInt(timeParts[0], 10)
    const minutes = timeParts[1]
    if (isNaN(hour)) return 'N/A'
    const ampm = hour >= 12 ? 'PM' : 'AM'
    const displayHour = hour % 12 || 12
    return `${displayHour}:${minutes} ${ampm}`
  } catch {
    return 'N/A'
  }
}

const RideCard = ({ ride, onBookRide }) => {
  const hasAvailableSeats = ride.availableSeats > 0
  // Get fare information from backend
  const fare = ride.totalFare || ride.fare || ride.price || null
  const distance = ride.distanceKm || null
  const currency = ride.currency || 'INR'
  // Get driver rating from backend (reviews received as driver)
  // Handle both null and undefined, and check if it's a valid number
  const driverRating = (ride.driverRating != null && ride.driverRating !== undefined && !isNaN(ride.driverRating)) 
    ? Number(ride.driverRating) 
    : null
  const driverTotalReviews = (ride.driverTotalReviews != null && ride.driverTotalReviews !== undefined) 
    ? Number(ride.driverTotalReviews) 
    : 0
  
  // Debug logging (remove in production)
  if (ride.driverName && (ride.driverRating == null || ride.driverRating === undefined)) {
    console.log('⚠️ RideCard: No driver rating found for ride:', {
      rideId: ride.id,
      driverId: ride.driverId,
      driverName: ride.driverName,
      driverRating: ride.driverRating,
      driverTotalReviews: ride.driverTotalReviews,
      fullRide: ride
    })
  }
  
  // Format fare display
  const formatFare = (amount) => {
    if (amount === null || amount === undefined) return 'TBD'
    if (typeof amount === 'number') {
      return `₹${amount.toFixed(2)}`
    }
    return amount
  }
  
  // Format distance display
  const formatDistance = (dist) => {
    if (dist === null || dist === undefined) return null
    if (typeof dist === 'number') {
      return `${dist.toFixed(1)} km`
    }
    return dist
  }

  return (
    <Card 
      variant="outlined" 
      sx={{ 
        '&:hover': { boxShadow: 4, transform: 'translateY(-2px)' },
        transition: 'all 0.3s ease',
        borderRadius: 2,
      }}
    >
      <CardContent>
        <Grid container spacing={3} alignItems="flex-start">
          <Grid item xs={12} md={8}>
            <Stack spacing={2}>
              {/* Route */}
              <Box>
                <Stack direction="row" alignItems="center" spacing={1} mb={1}>
                  <LocationOnRoundedIcon color="primary" fontSize="small" />
                  <Typography variant="h6" fontWeight={700}>
                {ride.source} → {ride.destination}
              </Typography>
                </Stack>
              </Box>

              <Divider />

              {/* Driver Info */}
              <Stack direction="row" spacing={2} flexWrap="wrap" gap={1.5}>
                {(ride.driverName || ride.driver?.name) && (
                  <Chip
                    icon={<PersonRoundedIcon />}
                    label={`Driver: ${ride.driverName || ride.driver?.name}`}
                    size="small"
                    variant="outlined"
                  />
                )}
                {driverRating != null && driverRating > 0 ? (
                  <Chip
                    label={`${driverRating.toFixed(1)} ⭐${driverTotalReviews > 0 ? ` (${driverTotalReviews})` : ''}`}
                    size="small"
                    color="primary"
                    sx={{ 
                      '& .MuiChip-label': {
                        display: 'flex',
                        alignItems: 'center',
                        gap: 0.5,
                      }
                    }}
                  />
                ) : (
                  <Chip
                    label="No ratings yet"
                    size="small"
                    variant="outlined"
                    color="default"
                  />
                )}
              </Stack>

              {/* Vehicle Details */}
              <Stack direction="row" spacing={2} flexWrap="wrap" gap={1.5}>
                {(ride.vehicleModel || ride.vehicle?.model) && (
                  <Chip
                    icon={<DirectionsCarRoundedIcon />}
                    label={ride.vehicleModel || ride.vehicle?.model}
                    size="small"
                    variant="outlined"
                  />
                )}
                {ride.vehicleLicensePlate && (
                  <Chip
                    label={`Plate: ${ride.vehicleLicensePlate}`}
                    size="small"
                    variant="outlined"
                  />
                )}
                {ride.vehicleColor && (
                  <Chip
                    label={`Color: ${ride.vehicleColor}`}
                    size="small"
                    variant="outlined"
                  />
                )}
              </Stack>

              {/* Date & Time */}
              <Stack direction="row" spacing={2} flexWrap="wrap" gap={1.5} alignItems="center">
                <Chip
                  icon={<AccessTimeRoundedIcon />}
                  label={`${formatDate(ride.rideDate || ride.date)} at ${formatTime(ride.rideTime || ride.time)}`}
                  size="small"
                  variant="outlined"
                />
                <Chip
                  label={`${ride.availableSeats ?? 0} seats available`}
                  size="small"
                  color={hasAvailableSeats ? 'success' : 'error'}
                />
                {distance && (
                  <Chip
                    icon={<StraightenRoundedIcon />}
                    label={formatDistance(distance)}
                    size="small"
                    variant="outlined"
                    color="primary"
                  />
                )}
              </Stack>

              {/* Notes */}
              {ride.notes && (
                <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', mt: 0.5 }}>
                  {ride.notes}
                </Typography>
              )}
            </Stack>
          </Grid>

          {/* Price and Book Button */}
          <Grid item xs={12} md={4}>
            <Stack spacing={2} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
              {/* Price Display */}
              <Box sx={{ textAlign: { xs: 'left', md: 'right' } }}>
                <Stack direction="row" alignItems="center" spacing={0.5} justifyContent={{ xs: 'flex-start', md: 'flex-end' }}>
                  <AttachMoneyRoundedIcon color="primary" />
                  <Typography variant="h5" fontWeight={700} color="primary">
                    {formatFare(fare)}
                  </Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  {fare !== null && fare !== undefined ? 'Total fare' : 'Fare TBD'}
                </Typography>
                {distance && (
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                    {formatDistance(distance)}
                  </Typography>
                )}
              </Box>

              {/* Book Button */}
              <Button
                variant="contained"
                startIcon={<BookmarkAddRoundedIcon />}
                onClick={() => onBookRide(ride)}
                disabled={!hasAvailableSeats}
                fullWidth
                size="large"
                sx={{ 
                  minWidth: { md: 180 },
                  py: 1.5,
                }}
              >
                Book Seat
              </Button>
              {!hasAvailableSeats && (
                <Typography variant="caption" color="error" sx={{ textAlign: { xs: 'left', md: 'right' } }}>
                  No seats available
                </Typography>
              )}
            </Stack>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  )
}

RideCard.propTypes = {
  ride: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    source: PropTypes.string,
    destination: PropTypes.string,
    rideDate: PropTypes.oneOfType([PropTypes.string, PropTypes.instanceOf(Date)]),
    date: PropTypes.oneOfType([PropTypes.string, PropTypes.instanceOf(Date)]),
    rideTime: PropTypes.string,
    time: PropTypes.string,
    availableSeats: PropTypes.number,
    vehicleModel: PropTypes.string,
    vehicleLicensePlate: PropTypes.string,
    vehicleColor: PropTypes.string,
    vehicle: PropTypes.shape({
      model: PropTypes.string,
    }),
    driverName: PropTypes.string,
    driverRating: PropTypes.number,
    rating: PropTypes.number,
    driver: PropTypes.shape({
      name: PropTypes.string,
    }),
    fare: PropTypes.number,
    price: PropTypes.number,
    totalFare: PropTypes.number,
    distanceKm: PropTypes.number,
    currency: PropTypes.string,
    notes: PropTypes.string,
  }).isRequired,
  onBookRide: PropTypes.func.isRequired,
}

export default RideCard

