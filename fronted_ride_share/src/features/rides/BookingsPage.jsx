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
import EmailRoundedIcon from '@mui/icons-material/EmailRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import { useDispatch, useSelector } from 'react-redux'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import PaymentStatusBadge from '../../components/payments/PaymentStatusBadge'
import PaymentDialog from '../../components/payments/PaymentDialog'
import { fetchMyBookings } from './rideSlice'
import { getPaymentOrderForRetry } from '../payments/paymentSlice'
import { createPassengerReview, checkUserReviewed } from '../reviews/reviewSlice'
import ReviewDialog from '../../components/reviews/ReviewDialog'
import RateReviewRoundedIcon from '@mui/icons-material/RateReviewRounded'
import { useState } from 'react'

const statusChipColor = {
  CONFIRMED: 'success',
  PENDING: 'warning',
  COMPLETED: 'success', // Changed to success (green) for completed rides
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
  const { paymentDetails } = useSelector((state) => state.payments)
  const { loading: reviewLoading, error: reviewError } = useSelector((state) => state.reviews)
  const [paymentDialogOpen, setPaymentDialogOpen] = useState(false)
  const [selectedBooking, setSelectedBooking] = useState(null)
  const [reviewDialogOpen, setReviewDialogOpen] = useState(false)
  const [bookingToReview, setBookingToReview] = useState(null)

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

  const handleRetryPayment = async (booking) => {
    setSelectedBooking(booking)
    
    // If booking already has paymentOrder with keyId, use it
    // Otherwise, fetch payment order for retry
    if (booking.paymentId && (!booking.paymentOrder || !booking.paymentOrder.keyId)) {
      try {
        const paymentOrderAction = await dispatch(getPaymentOrderForRetry(booking.paymentId))
        if (paymentOrderAction.type.endsWith('fulfilled')) {
          // Update booking with payment order
          const updatedBooking = {
            ...booking,
            paymentOrder: paymentOrderAction.payload,
          }
          setSelectedBooking(updatedBooking)
        } else {
          console.error('Failed to fetch payment order:', paymentOrderAction.payload)
          alert('Failed to load payment details. Please try again.')
          return // Don't open dialog if we can't get payment order
        }
      } catch (error) {
        console.error('Failed to fetch payment order:', error)
        alert('Failed to load payment details. Please try again.')
        return
      }
    }
    
    setPaymentDialogOpen(true)
  }

  const handlePaymentSuccess = () => {
    dispatch(fetchMyBookings())
  }

  const handleReviewClick = async (booking) => {
    try {
      // Check if already reviewed
      const hasReviewedAction = await dispatch(checkUserReviewed(booking.id))
      
      // Check if the action was fulfilled and what the payload is
      if (hasReviewedAction.type.endsWith('fulfilled')) {
        const hasReviewed = hasReviewedAction.payload
        console.log('Review check result:', { bookingId: booking.id, hasReviewed })
        
        if (hasReviewed === true) {
          alert('You have already reviewed this driver')
          return
        }
      } else if (hasReviewedAction.type.endsWith('rejected')) {
        // If check fails, log error but allow review (might be a network issue)
        console.error('Failed to check review status:', hasReviewedAction.payload)
        // Continue to allow review - don't block on check failure
      }
      
      setBookingToReview(booking)
      setReviewDialogOpen(true)
    } catch (error) {
      console.error('Error checking review:', error)
      // On error, allow user to proceed (might be network issue)
      setBookingToReview(booking)
      setReviewDialogOpen(true)
    }
  }

  const handleSubmitReview = async ({ rating, comment }) => {
    if (bookingToReview) {
      const action = await dispatch(createPassengerReview({
        bookingId: bookingToReview.id,
        rating,
        comment,
      }))
      if (action.type.endsWith('fulfilled')) {
        setReviewDialogOpen(false)
        setBookingToReview(null)
        alert('Review submitted successfully!')
        dispatch(fetchMyBookings())
      }
    }
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
                      <Stack direction="row" spacing={1} flexWrap="wrap" gap={1}>
                        {/* Show booking status - use success color for completed bookings */}
                        {booking.status === 'COMPLETED' && booking.passengerConfirmed ? (
                          <Chip
                            icon={<CheckCircleRoundedIcon />}
                            label="OTP Verified & Payment Released"
                            color="success"
                            size="small"
                            sx={{
                              fontWeight: 600,
                              bgcolor: 'success.main',
                              color: 'white',
                              '& .MuiChip-icon': {
                                color: 'white',
                              },
                            }}
                          />
                        ) : (
                          <Chip
                            label={
                              booking.status === 'PENDING' 
                                ? 'Booking Pending' 
                                : booking.status === 'CONFIRMED'
                                ? 'Booking Confirmed'
                                : booking.status
                            }
                            color={statusChipColor[booking.status] ?? 'default'}
                            size="small"
                          />
                        )}
                        <Chip
                          label={`${booking.seatsBooked} seat${booking.seatsBooked > 1 ? 's' : ''}`}
                          icon={<EventSeatRoundedIcon />}
                          size="small"
                        />
                        {/* Show payment status badge only if booking is not completed */}
                        {booking.status !== 'COMPLETED' && booking.paymentId && (
                          <PaymentStatusBadge 
                            status={
                              paymentDetails?.id === booking.paymentId 
                                ? paymentDetails?.status 
                                : 'PENDING'
                            } 
                          />
                        )}
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

                {/* OTP Notification - Show when driver has confirmed but passenger hasn't */}
                {booking.driverConfirmed && !booking.passengerConfirmed && booking.status === 'CONFIRMED' && (
                  <Alert 
                    severity="info" 
                    icon={<EmailRoundedIcon />}
                    sx={{ mb: 2 }}
                  >
                    <Typography variant="body2" fontWeight={600}>
                      Ride Completion OTP Sent
                    </Typography>
                    <Typography variant="body2">
                      The driver has marked this ride as completed. An OTP has been sent to your email address. 
                      Please check your email and share the OTP with the driver to confirm completion and release payment.
                    </Typography>
                    <Typography variant="caption" sx={{ mt: 1, display: 'block' }}>
                      Check your email inbox for the 6-digit OTP code. The OTP is valid for 10 minutes.
                    </Typography>
                  </Alert>
                )}

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
                            {/* CRITICAL: passengerFare is already multiplied by seats (fare per seat * seats booked) */}
                            Your fare ({booking.seatsBooked || 1} seat{booking.seatsBooked > 1 ? 's' : ''})
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

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center" justifyContent="space-between">
                  <Stack direction="row" spacing={2} alignItems="center" flex={1}>
                    <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main' }}>
                      <DirectionsCarRoundedIcon />
                    </Avatar>
                    <Box>
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
                  {/* Only show payment button if booking is not completed */}
                  {booking.status === 'PENDING' && booking.paymentId && !booking.passengerConfirmed && (
                    <Button
                      variant="contained"
                      color="primary"
                      onClick={() => handleRetryPayment(booking)}
                    >
                      Complete Payment
                    </Button>
                  )}
                  {/* Show completed indicator if ride is completed via OTP verification */}
                  {booking.status === 'COMPLETED' && booking.passengerConfirmed && (
                    <Stack spacing={1}>
                      <Chip
                        icon={<CheckCircleRoundedIcon />}
                        label="OTP Verified & Payment Released"
                        color="success"
                        sx={{
                          fontWeight: 600,
                          bgcolor: 'success.main',
                          color: 'white',
                          px: 2,
                          py: 1,
                          '& .MuiChip-icon': {
                            color: 'white',
                            fontSize: '1.2rem',
                          },
                        }}
                      />
                      <Button
                        variant="outlined"
                        color="primary"
                        onClick={() => handleReviewClick(booking)}
                        startIcon={<RateReviewRoundedIcon />}
                        size="small"
                      >
                        Rate Driver
                      </Button>
                    </Stack>
                  )}
                </Stack>
              </Paper>
            )
          })}
        </Stack>
      )}

      {/* Payment Dialog */}
      <PaymentDialog
        open={paymentDialogOpen}
        onClose={() => {
          setPaymentDialogOpen(false)
          setSelectedBooking(null)
        }}
        booking={selectedBooking}
        paymentOrder={
          selectedBooking?.paymentOrder || 
          (paymentDetails?.paymentOrder && paymentDetails?.id === selectedBooking?.paymentId 
            ? paymentDetails.paymentOrder 
            : null)
        }
        onPaymentSuccess={handlePaymentSuccess}
      />
      <ReviewDialog
        open={reviewDialogOpen}
        onClose={() => {
          setReviewDialogOpen(false)
          setBookingToReview(null)
        }}
        onSubmit={handleSubmitReview}
        loading={reviewLoading}
        error={reviewError}
        title="Rate Driver"
        subtitle={bookingToReview ? `Rate your experience with ${bookingToReview.rideDetails?.driverName || 'the driver'} for this ride` : ''}
      />
    </PageContainer>
  )
}

export default BookingsPage


