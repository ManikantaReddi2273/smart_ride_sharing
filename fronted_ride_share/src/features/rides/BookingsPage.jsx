import { useEffect, useMemo } from 'react'
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  Divider,
  Grid,
  LinearProgress,
  Paper,
  Stack,
  Typography,
} from '@mui/material'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import BookOnlineRoundedIcon from '@mui/icons-material/BookOnlineRounded'
import RouteRoundedIcon from '@mui/icons-material/RouteRounded'
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded'
import EventSeatRoundedIcon from '@mui/icons-material/EventSeatRounded'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import AttachMoneyRoundedIcon from '@mui/icons-material/AttachMoneyRounded'
import StraightenRoundedIcon from '@mui/icons-material/StraightenRounded'
import { useDispatch, useSelector } from 'react-redux'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import { fetchMyBookings } from './rideSlice'

const statusChipColor = {
  CONFIRMED: 'success',
  PENDING: 'warning',
  COMPLETED: 'default',
  CANCELLED: 'error',
}

const formatDate = (value) => {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleDateString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  } catch (e) {
    return value
  }
}

const formatTime = (value) => {
  if (!value) return '—'
  try {
    const [hour, minute] = value.split(':')
    const date = new Date()
    date.setHours(Number(hour), Number(minute))
    return date.toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit',
    })
  } catch (e) {
    return value
  }
}

const BookingsPage = () => {
  const dispatch = useDispatch()
  const { myBookings, status, error } = useSelector((state) => state.rides)

  useEffect(() => {
    dispatch(fetchMyBookings())
  }, [dispatch])

  const isLoading = status === 'loading'

  const sortedBookings = useMemo(
    () =>
      [...myBookings].sort((a, b) => {
        const aDate = new Date(a?.rideDetails?.rideDate ?? 0).getTime()
        const bDate = new Date(b?.rideDetails?.rideDate ?? 0).getTime()
        return bDate - aDate
      }),
    [myBookings],
  )

  const handleRefresh = () => {
    dispatch(fetchMyBookings())
  }

  return (
    <PageContainer>
      <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            My Bookings
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Track every ride you have reserved with drivers
          </Typography>
        </Box>
        <Button
          variant="outlined"
          color="primary"
          startIcon={<RefreshRoundedIcon />}
          onClick={handleRefresh}
          disabled={isLoading}
        >
          Refresh
        </Button>
      </Box>

      {isLoading && <LinearProgress />}
      {error && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {error}
        </Alert>
      )}

      {!isLoading && sortedBookings.length === 0 ? (
        <Paper sx={{ mt: 2 }}>
          <EmptyState
            title="No bookings yet"
            description="When you reserve a seat on a ride, it will show up here."
            icon="🧾"
          />
        </Paper>
      ) : (
        <Stack spacing={2} sx={{ position: 'relative' }}>
          {sortedBookings.map((booking) => {
            const ride = booking.rideDetails ?? {}
            return (
              <Paper
                key={booking.id}
                sx={{
                  p: 3,
                  borderRadius: 3,
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Grid container spacing={3}>
                  <Grid item xs={12} md={6} lg={5}>
                    <Stack spacing={1.5}>
                      <Stack direction="row" spacing={2} alignItems="center">
                        <Avatar>
                          <BookOnlineRoundedIcon />
                        </Avatar>
                        <Box>
                          <Typography variant="subtitle2" color="text.secondary">
                            Booking #{booking.id}
                          </Typography>
                          <Typography variant="h6" fontWeight={700}>
                            {ride.source} → {ride.destination}
                          </Typography>
                        </Box>
                      </Stack>
                      <Stack direction="row" spacing={1}>
                        <Chip
                          label={booking.status}
                          color={statusChipColor[booking.status] ?? 'default'}
                          size="small"
                        />
                        <Chip
                          label={`${booking.seatsBooked} seat${booking.seatsBooked > 1 ? 's' : ''}`}
                          icon={<EventSeatRoundedIcon />}
                          size="small"
                        />
                      </Stack>
                    </Stack>
                  </Grid>

                  <Grid item xs={12} md={3} lg={4}>
                    <Stack spacing={1.5}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <RouteRoundedIcon color="primary" fontSize="small" />
                        <Typography variant="body2" color="text.secondary">
                          Route
                        </Typography>
                      </Stack>
                      <Typography variant="body1">{ride.source}</Typography>
                      <Typography variant="body1">to</Typography>
                      <Typography variant="body1">{ride.destination}</Typography>
                    </Stack>
                  </Grid>

                  <Grid item xs={12} md={3} lg={3}>
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <AccessTimeRoundedIcon color="primary" fontSize="small" />
                        <Typography variant="body2" color="text.secondary">
                          Schedule
                        </Typography>
                      </Stack>
                      <Typography variant="body1">{formatDate(ride.rideDate)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {formatTime(ride.rideTime)}
                      </Typography>
                    </Stack>
                  </Grid>
                </Grid>

                <Divider sx={{ my: 2 }} />

                {/* Fare Information */}
                {(booking.passengerFare || booking.passengerDistanceKm) && (
                  <Box sx={{ mb: 2, p: 2, bgcolor: 'rgba(15, 139, 141, 0.04)', borderRadius: 2 }}>
                    <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                      {booking.passengerFare && (
                        <Stack direction="row" spacing={1} alignItems="center">
                          <AttachMoneyRoundedIcon color="primary" fontSize="small" />
                          <Typography variant="h6" fontWeight={700} color="primary">
                            ₹{booking.passengerFare.toFixed(2)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Your fare
                          </Typography>
                        </Stack>
                      )}
                      {booking.passengerDistanceKm && (
                        <Stack direction="row" spacing={1} alignItems="center">
                          <StraightenRoundedIcon color="primary" fontSize="small" />
                          <Typography variant="body2" color="text.secondary">
                            {booking.passengerDistanceKm.toFixed(1)} km
                          </Typography>
                        </Stack>
                      )}
                      {booking.passengerSource && booking.passengerSource !== ride.source && (
                        <Typography variant="caption" color="text.secondary">
                          Joining at: {booking.passengerSource}
                        </Typography>
                      )}
                      {booking.passengerDestination && booking.passengerDestination !== ride.destination && (
                        <Typography variant="caption" color="text.secondary">
                          Exiting at: {booking.passengerDestination}
                        </Typography>
                      )}
                    </Stack>
                  </Box>
                )}

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center">
                  <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main' }}>
                    <DirectionsCarRoundedIcon />
                  </Avatar>
                  <Box flex={1}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Driver
                    </Typography>
                    <Typography variant="body1" fontWeight={600}>
                      {ride.driverName || 'Driver details unavailable'}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={2}>
                    {ride.vehicleModel && (
                      <Chip
                        variant="outlined"
                        icon={<DirectionsCarRoundedIcon fontSize="small" />}
                        label={ride.vehicleModel}
                      />
                    )}
                    {ride.vehicleLicensePlate && (
                      <Chip variant="outlined" label={ride.vehicleLicensePlate} />
                    )}
                  </Stack>
                </Stack>
              </Paper>
            )
          })}
        </Stack>
      )}
    </PageContainer>
  )
}

export default BookingsPage


