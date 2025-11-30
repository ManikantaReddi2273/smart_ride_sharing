import { useEffect, useState } from 'react'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'

import PageContainer from '../../components/common/PageContainer'
import DriverDashboard from './components/DriverDashboard'
import PassengerDashboard from './components/PassengerDashboard'
import RideCard from '../../components/rides/RideCard'
import EmptyState from '../../components/common/EmptyState'
import { fetchMyBookings, fetchMyRides, searchRides, bookRide } from '../rides/rideSlice'

const bookingSchema = yup.object().shape({
  seats: yup
    .number()
    .typeError('Enter a valid number')
    .min(1, 'Minimum 1 seat')
    .required('Number of seats is required'),
  notes: yup.string().max(160, 'Keep it under 160 characters'),
})

const DashboardPage = () => {
  const dispatch = useDispatch()
  const { user } = useSelector((state) => state.auth)
  const { myRides, myBookings, searchResults, lastSearchCriteria, status, error } = useSelector(
    (state) => state.rides,
  )
  const [selectedRide, setSelectedRide] = useState(null)

  const {
    register: registerBooking,
    handleSubmit: handleBookingSubmit,
    reset: resetBooking,
    formState: { errors: bookingErrors },
  } = useForm({
    resolver: yupResolver(bookingSchema),
    defaultValues: {
      seats: 1,
      notes: '',
    },
  })

  useEffect(() => {
    if (!user) return
    // All users can fetch both their rides and bookings
    dispatch(fetchMyRides())
    dispatch(fetchMyBookings())
  }, [dispatch, user])

  const onOpenBooking = (ride) => {
    setSelectedRide(ride)
  }

  const onCloseBooking = () => {
    setSelectedRide(null)
    resetBooking()
  }

  const onConfirmBooking = (values) => {
    if (!selectedRide) return
    dispatch(
      bookRide({
        rideId: selectedRide.id,
        passengerDetails: {
          seats: values.seats,
          notes: values.notes,
        },
      }),
    ).then((action) => {
      if (action.type.endsWith('fulfilled')) {
        onCloseBooking()
        // Refresh data
        dispatch(fetchMyRides())
        dispatch(fetchMyBookings())
        // Refresh search results if they exist
        if (lastSearchCriteria) {
          dispatch(searchRides(lastSearchCriteria))
        }
      }
    })
  }

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

  return (
    <PageContainer>
      <Box mb={4}>
        <Typography variant="h4" fontWeight={700} mb={1}>
          Welcome back, {user?.name || user?.email || 'User'}! 👋
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your rides and track your bookings
        </Typography>
      </Box>

      {/* Search Results Section */}
      {searchResults && searchResults.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
              <Box>
                <Typography variant="h6" fontWeight={700}>
                  Search Results
                </Typography>
                {lastSearchCriteria && (
                  <Typography variant="body2" color="text.secondary">
                    {lastSearchCriteria.source} → {lastSearchCriteria.destination}
                    {lastSearchCriteria.date && ` on ${formatDate(lastSearchCriteria.date)}`}
                  </Typography>
                )}
              </Box>
              <Chip
                label={`${searchResults.length} rides found`}
                color="primary"
              />
            </Stack>

            <Stack spacing={2}>
              {searchResults.map((ride) => (
                <RideCard key={ride.id} ride={ride} onBookRide={onOpenBooking} />
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Show combined dashboard for all users - they can both post and book rides */}
      <DriverDashboard 
        rides={myRides} 
        bookings={myBookings} 
        loading={status === 'loading'}
        userId={user?.id}
      />

      {/* Booking Dialog */}
      <Dialog open={Boolean(selectedRide)} onClose={onCloseBooking} fullWidth maxWidth="sm">
        <DialogTitle>Book Ride</DialogTitle>
        <Box component="form" onSubmit={handleBookingSubmit(onConfirmBooking)}>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
            {error && status === 'failed' && (
              <Typography color="error" variant="body2" sx={{ mb: 1 }}>
                {error}
              </Typography>
            )}
            {selectedRide && (
              <>
                <Typography variant="subtitle1" fontWeight={600}>
                  {selectedRide.source} → {selectedRide.destination}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {formatDate(selectedRide.rideDate || selectedRide.date)} at{' '}
                  {formatTime(selectedRide.rideTime || selectedRide.time)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Available seats: {selectedRide.availableSeats ?? 0}
                </Typography>
                {(selectedRide.vehicleModel || selectedRide.vehicle?.model) && (
                  <Typography variant="body2" color="text.secondary">
                    Vehicle: {selectedRide.vehicleModel || selectedRide.vehicle?.model}
                    {selectedRide.vehicleLicensePlate && ` (${selectedRide.vehicleLicensePlate})`}
                  </Typography>
                )}
                {(selectedRide.driverName || selectedRide.driver?.name) && (
                  <Typography variant="body2" color="text.secondary">
                    Driver: {selectedRide.driverName || selectedRide.driver?.name}
                  </Typography>
                )}
              </>
            )}
            <TextField
              label="Number of seats"
              type="number"
              {...registerBooking('seats')}
              error={!!bookingErrors.seats}
              helperText={bookingErrors.seats?.message}
              fullWidth
              inputProps={{ min: 1, max: selectedRide?.availableSeats ?? 1 }}
            />
            <TextField
              label="Notes to driver (optional)"
              multiline
              minRows={3}
              {...registerBooking('notes')}
              error={!!bookingErrors.notes}
              helperText={bookingErrors.notes?.message}
              fullWidth
              placeholder="Any special requests or pickup instructions..."
            />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 3 }}>
            <Button onClick={onCloseBooking}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={status === 'loading'}>
              {status === 'loading' ? 'Booking...' : 'Confirm Booking'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </PageContainer>
  )
}

export default DashboardPage

