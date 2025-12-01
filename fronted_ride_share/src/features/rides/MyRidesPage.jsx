import { useEffect, useState } from 'react'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Alert,
  Tooltip,
  TextField,
  Divider,
} from '@mui/material'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded'
import EventRoundedIcon from '@mui/icons-material/EventRounded'
import PeopleRoundedIcon from '@mui/icons-material/PeopleRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import VerifiedRoundedIcon from '@mui/icons-material/VerifiedRounded'
import EmailRoundedIcon from '@mui/icons-material/EmailRounded'
import CancelRoundedIcon from '@mui/icons-material/CancelRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import { useDispatch, useSelector } from 'react-redux'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import LoadingOverlay from '../../components/common/LoadingOverlay'
import { fetchMyRides, updateRideStatus, clearRideErrors, verifyOtp, sendOtpToPassenger, updateRide, cancelRide } from './rideSlice'
import { createDriverReview, checkUserReviewed } from '../reviews/reviewSlice'
import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'
import RescheduleDialog from '../../components/rides/RescheduleDialog'
import ReviewDialog from '../../components/reviews/ReviewDialog'
import RateReviewRoundedIcon from '@mui/icons-material/RateReviewRounded'

const MyRidesPage = () => {
  const dispatch = useDispatch()
  const { myRides, status, error } = useSelector((state) => state.rides)
  const [completeDialogOpen, setCompleteDialogOpen] = useState(false)
  const [otpDialogOpen, setOtpDialogOpen] = useState(false)
  const [selectedRide, setSelectedRide] = useState(null)
  const [bookings, setBookings] = useState([])
  const [otpInputs, setOtpInputs] = useState({}) // { bookingId: otp }
  const [otpSentBookings, setOtpSentBookings] = useState(new Set()) // Track which bookings have OTP sent
  const [otpErrors, setOtpErrors] = useState({}) // { bookingId: errorMessage }
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false)
  const [rescheduleDialogOpen, setRescheduleDialogOpen] = useState(false)
  const [rideToCancel, setRideToCancel] = useState(null)
  const [rideToReschedule, setRideToReschedule] = useState(null)
  const [reviewDialogOpen, setReviewDialogOpen] = useState(false)
  const [bookingToReview, setBookingToReview] = useState(null)
  const { loading: reviewLoading, error: reviewError } = useSelector((state) => state.reviews)

  useEffect(() => {
    dispatch(fetchMyRides())
  }, [dispatch])

  const handleMarkComplete = (ride) => {
    setSelectedRide(ride)
    setCompleteDialogOpen(true)
  }

  const confirmMarkComplete = async () => {
    if (selectedRide) {
      const action = await dispatch(updateRideStatus({ rideId: selectedRide.id, status: 'COMPLETED' }))
      if (action.type.endsWith('fulfilled')) {
        setCompleteDialogOpen(false)
        // Fetch bookings for this ride to show "Send OTP" buttons
        try {
          const { data } = await apiClient.get(endpoints.rides.rideBookings(selectedRide.id))
          const pendingBookings = data.filter(
            (b) => b.status === 'CONFIRMED' && b.driverConfirmed && !b.passengerConfirmed
          )
          setBookings(pendingBookings)
          setOtpSentBookings(new Set()) // Reset OTP sent tracking
          if (pendingBookings.length > 0) {
            setOtpDialogOpen(true)
          } else {
            dispatch(fetchMyRides())
          }
        } catch (err) {
          console.error('Failed to fetch bookings:', err)
          dispatch(fetchMyRides())
        }
      }
    }
  }

  const handleSendOtp = async (bookingId) => {
    // Clear previous error for this booking
    setOtpErrors((prev) => {
      const newErrors = { ...prev }
      delete newErrors[bookingId]
      return newErrors
    })
    
    const action = await dispatch(sendOtpToPassenger(bookingId))
    if (action.type.endsWith('fulfilled')) {
      // Mark that OTP has been sent for this booking
      setOtpSentBookings((prev) => new Set([...prev, bookingId]))
      // Refresh bookings to get updated OTP status
      if (selectedRide) {
        try {
          const { data } = await apiClient.get(endpoints.rides.rideBookings(selectedRide.id))
          const pendingBookings = data.filter(
            (b) => b.status === 'CONFIRMED' && b.driverConfirmed && !b.passengerConfirmed
          )
          setBookings(pendingBookings)
        } catch (err) {
          console.error('Failed to refresh bookings:', err)
        }
      }
    } else if (action.type.endsWith('rejected')) {
      // Handle error case
      const errorMessage = action.payload || 'Failed to send OTP. Please try again.'
      setOtpErrors((prev) => ({ ...prev, [bookingId]: errorMessage }))
      console.error('Failed to send OTP:', errorMessage)
    }
  }

  const handleOtpChange = (bookingId, otp) => {
    setOtpInputs((prev) => ({ ...prev, [bookingId]: otp }))
  }

  const handleVerifyOtp = async (bookingId) => {
    const otp = otpInputs[bookingId]
    if (!otp || otp.trim().length !== 6) {
      alert('Please enter a valid 6-digit OTP')
      return
    }

    const action = await dispatch(verifyOtp({ bookingId, otp: otp.trim() }))
    if (action.type.endsWith('fulfilled')) {
      // Remove verified booking from list
      setBookings((prev) => prev.filter((b) => b.id !== bookingId))
      delete otpInputs[bookingId]
      setOtpInputs({ ...otpInputs })
      
      // If all bookings verified, close dialog and refresh
      const remaining = bookings.filter((b) => b.id !== bookingId)
      if (remaining.length === 0) {
        setOtpDialogOpen(false)
        dispatch(fetchMyRides())
      }
    }
  }

  const handleCancelRide = (ride) => {
    setRideToCancel(ride)
    setCancelDialogOpen(true)
  }

  const confirmCancelRide = async () => {
    if (rideToCancel) {
      const action = await dispatch(cancelRide(rideToCancel.id))
      if (action.type.endsWith('fulfilled')) {
        setCancelDialogOpen(false)
        setRideToCancel(null)
        dispatch(fetchMyRides())
      }
    }
  }

  const handleRescheduleRide = (ride) => {
    setRideToReschedule(ride)
    setRescheduleDialogOpen(true)
  }

  const confirmRescheduleRide = async (rescheduleData) => {
    if (rideToReschedule) {
      const action = await dispatch(updateRide({ 
        rideId: rideToReschedule.id, 
        rideData: rescheduleData 
      }))
      if (action.type.endsWith('fulfilled')) {
        setRescheduleDialogOpen(false)
        setRideToReschedule(null)
        dispatch(fetchMyRides())
      }
    }
  }

  const handleReviewClick = async (booking) => {
    // Check if already reviewed
    const hasReviewedAction = await dispatch(checkUserReviewed(booking.id))
    if (hasReviewedAction.payload) {
      alert('You have already reviewed this passenger')
      return
    }
    setBookingToReview(booking)
    setReviewDialogOpen(true)
  }

  const handleSubmitReview = async ({ rating, comment }) => {
    if (bookingToReview) {
      const action = await dispatch(createDriverReview({
        bookingId: bookingToReview.id,
        rating,
        comment,
      }))
      if (action.type.endsWith('fulfilled')) {
        setReviewDialogOpen(false)
        setBookingToReview(null)
        alert('Review submitted successfully!')
        // Refresh bookings if OTP dialog is open
        if (otpDialogOpen && selectedRide) {
          try {
            const { data } = await apiClient.get(endpoints.rides.rideBookings(selectedRide.id))
            const pendingBookings = data.filter(
              (b) => b.status === 'CONFIRMED' && b.driverConfirmed && !b.passengerConfirmed
            )
            setBookings(pendingBookings)
          } catch (err) {
            console.error('Failed to refresh bookings:', err)
          }
        }
      }
    }
  }

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A'
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
  }

  const formatTime = (timeString) => {
    if (!timeString) return 'N/A'
    // Handle both "HH:mm:ss" and "HH:mm" formats
    const [hours, minutes] = timeString.split(':')
    const hour = parseInt(hours, 10)
    const ampm = hour >= 12 ? 'PM' : 'AM'
    const displayHour = hour % 12 || 12
    return `${displayHour}:${minutes} ${ampm}`
  }

  const getStatusColor = (status) => {
    switch (status?.toUpperCase()) {
      case 'POSTED':
        return 'info'
      case 'CONFIRMED':
        return 'success'
      case 'IN_PROGRESS':
        return 'warning'
      case 'COMPLETED':
        return 'default'
      case 'CANCELLED':
        return 'error'
      default:
        return 'default'
    }
  }

  if (status === 'loading') {
    return <LoadingOverlay />
  }

  return (
    <PageContainer>
      <Card>
        <CardContent>
          <Stack direction="row" alignItems="center" spacing={2} mb={3}>
            <Box
              sx={{
                width: 48,
                height: 48,
                borderRadius: 2,
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'rgba(15, 139, 141, 0.12)',
                color: 'primary.main',
              }}
            >
              <DirectionsCarRoundedIcon />
            </Box>
            <div>
              <Typography variant="h6">My Rides</Typography>
              <Typography variant="body2" color="text.secondary">
                View and manage all your posted rides
              </Typography>
            </div>
          </Stack>

          {myRides && myRides.length === 0 ? (
            <EmptyState
              icon={<DirectionsCarRoundedIcon />}
              title="No rides posted yet"
              message="Start sharing your rides by posting your first ride!"
            />
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Route
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Date & Time
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Seats
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Vehicle
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Status
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Typography variant="subtitle2" fontWeight={600}>
                        Actions
                      </Typography>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {myRides?.map((ride) => (
                    <TableRow key={ride.id} hover>
                      <TableCell>
                        <Stack spacing={0.5}>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <LocationOnRoundedIcon fontSize="small" color="primary" />
                            <Typography variant="body2" fontWeight={500}>
                              {ride.source || 'N/A'}
                            </Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1} pl={3}>
                            <Typography variant="caption" color="text.secondary">
                              ↓
                            </Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <LocationOnRoundedIcon fontSize="small" color="error" />
                            <Typography variant="body2" fontWeight={500}>
                              {ride.destination || 'N/A'}
                            </Typography>
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.5}>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <EventRoundedIcon fontSize="small" color="action" />
                            <Typography variant="body2">{formatDate(ride.rideDate)}</Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <AccessTimeRoundedIcon fontSize="small" color="action" />
                            <Typography variant="body2">{formatTime(ride.rideTime)}</Typography>
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <PeopleRoundedIcon fontSize="small" color="action" />
                          <Typography variant="body2">
                            {ride.availableSeats || 0} / {ride.totalSeats || 0}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {ride.vehicleModel || 'N/A'}
                        </Typography>
                        {ride.vehicleLicensePlate && (
                          <Typography variant="caption" color="text.secondary">
                            {ride.vehicleLicensePlate}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={ride.status || 'POSTED'}
                          color={getStatusColor(ride.status)}
                          size="small"
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="row" spacing={0.5} justifyContent="center">
                          {(ride.status === 'POSTED' || ride.status === 'BOOKED') && (
                            <>
                              <Tooltip title="Reschedule Ride">
                                <IconButton
                                  size="small"
                                  color="primary"
                                  onClick={() => handleRescheduleRide(ride)}
                                  disabled={status === 'loading'}
                                >
                                  <EditRoundedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Cancel Ride">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => handleCancelRide(ride)}
                                  disabled={status === 'loading'}
                                >
                                  <CancelRoundedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}
                        {(ride.status === 'BOOKED' || ride.status === 'IN_PROGRESS') && (
                          <Tooltip title="Mark as Completed">
                            <IconButton
                              size="small"
                              color="success"
                              onClick={() => handleMarkComplete(ride)}
                              disabled={status === 'loading'}
                            >
                              <CheckCircleRoundedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                        {ride.status === 'COMPLETED' && (
                          <Tooltip title="Manage OTP Verification">
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={async () => {
                                setSelectedRide(ride)
                                try {
                                  const { data } = await apiClient.get(endpoints.rides.rideBookings(ride.id))
                                  const pendingBookings = data.filter(
                                    (b) => b.status === 'CONFIRMED' && b.driverConfirmed && !b.passengerConfirmed
                                  )
                                  setBookings(pendingBookings)
                                  setOtpSentBookings(new Set(data.filter(b => b.hasOtp).map(b => b.id)))
                                  if (pendingBookings.length > 0) {
                                    setOtpDialogOpen(true)
                                  } else {
                                    alert('All passengers have been verified!')
                                  }
                                } catch (err) {
                                  console.error('Failed to fetch bookings:', err)
                                  alert('Failed to load bookings. Please try again.')
                                }
                              }}
                            >
                              <EmailRoundedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      {/* Mark as Completed Dialog */}
      <Dialog open={completeDialogOpen} onClose={() => setCompleteDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Mark Ride as Completed</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => dispatch(clearRideErrors())}>
                {error}
              </Alert>
            )}
            {selectedRide && (
              <>
                <Typography variant="body1">
                  Are you sure you want to mark this ride as completed?
                </Typography>
                <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                  <Stack spacing={1}>
                    <Typography variant="body2">
                      <strong>Route:</strong> {selectedRide.source} → {selectedRide.destination}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Date:</strong> {formatDate(selectedRide.rideDate)} at {formatTime(selectedRide.rideTime)}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Vehicle:</strong> {selectedRide.vehicleModel} ({selectedRide.vehicleLicensePlate})
                    </Typography>
                  </Stack>
                </Box>
                <Alert severity="info">
                  <Typography variant="body2" fontWeight={600}>
                    Important:
                  </Typography>
                  <Typography variant="body2">
                    • After marking as completed, you'll see a list of passengers
                  </Typography>
                  <Typography variant="body2">
                    • Click "Send OTP" for each passenger to send OTP to their email
                  </Typography>
                  <Typography variant="body2">
                    • Ask passenger for OTP and enter it to verify and receive payment
                  </Typography>
                  <Typography variant="body2">
                    • Make sure all passengers have been dropped off before marking as completed
                  </Typography>
                  <Typography variant="body2">
                    • This action cannot be undone
                  </Typography>
                </Alert>
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCompleteDialogOpen(false)} disabled={status === 'loading'}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={confirmMarkComplete}
            disabled={status === 'loading'}
            startIcon={<CheckCircleRoundedIcon />}
          >
            {status === 'loading' ? 'Processing...' : 'Mark as Completed'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* OTP Management Dialog */}
      <Dialog open={otpDialogOpen} onClose={() => {}} maxWidth="md" fullWidth>
        <DialogTitle>Complete Ride - Send OTP to Passengers</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <Alert severity="info">
              <Typography variant="body2" fontWeight={600}>
                Send OTP to each passenger individually
              </Typography>
              <Typography variant="body2">
                Click "Send OTP" for each passenger. Once sent, ask the passenger for the OTP and enter it below to verify and receive payment.
              </Typography>
            </Alert>

            {bookings.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                All bookings have been verified.
              </Typography>
            ) : (
              bookings.map((booking) => {
                const hasOtpSent = otpSentBookings.has(booking.id) || booking.hasOtp
                return (
                  <Box key={booking.id} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                    <Stack spacing={2}>
                      <Box>
                        <Typography variant="subtitle2" fontWeight={600}>
                          Passenger: {booking.passengerName || `ID: ${booking.passengerId}`}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Seats: {booking.seatsBooked} | Fare: ₹{booking.passengerFare || 'N/A'}
                        </Typography>
                        {booking.passengerEmail && (
                          <Typography variant="caption" color="text.secondary">
                            Email: {booking.passengerEmail}
                          </Typography>
                        )}
                      </Box>
                      
                      {otpErrors[booking.id] && (
                        <Alert severity="error" sx={{ mb: 1 }} onClose={() => {
                          setOtpErrors((prev) => {
                            const newErrors = { ...prev }
                            delete newErrors[booking.id]
                            return newErrors
                          })
                        }}>
                          {otpErrors[booking.id]}
                        </Alert>
                      )}
                      
                      {!hasOtpSent ? (
                        <Button
                          variant="contained"
                          color="primary"
                          onClick={() => handleSendOtp(booking.id)}
                          disabled={status === 'loading'}
                          startIcon={<EmailRoundedIcon />}
                          fullWidth
                        >
                          {status === 'loading' ? 'Sending...' : `Send OTP to ${booking.passengerName || 'Passenger'}`}
                        </Button>
                      ) : (
                        <Stack spacing={2}>
                          <Alert severity="success" sx={{ py: 0.5 }}>
                            <Typography variant="body2">
                              ✓ OTP sent to {booking.passengerEmail || 'passenger email'}
                            </Typography>
                            <Typography variant="caption">
                              Ask the passenger for the 6-digit OTP and enter it below
                            </Typography>
                          </Alert>
                          <Stack direction="row" spacing={2} alignItems="center">
                            <TextField
                              label="Enter 6-digit OTP from passenger"
                              value={otpInputs[booking.id] || ''}
                              onChange={(e) => {
                                const value = e.target.value.replace(/\D/g, '').slice(0, 6)
                                handleOtpChange(booking.id, value)
                              }}
                              inputProps={{ 
                                maxLength: 6, 
                                style: { textAlign: 'center', fontSize: '20px', letterSpacing: '4px' } 
                              }}
                              placeholder="000000"
                              size="small"
                              sx={{ flex: 1 }}
                              error={error && error.includes('OTP')}
                            />
                            <Button
                              variant="contained"
                              color="success"
                              onClick={() => handleVerifyOtp(booking.id)}
                              disabled={!otpInputs[booking.id] || otpInputs[booking.id].length !== 6 || status === 'loading'}
                              startIcon={<VerifiedRoundedIcon />}
                            >
                              {status === 'loading' ? 'Verifying...' : 'Verify'}
                            </Button>
                          </Stack>
                        </Stack>
                      )}
                      {/* Show Review button for completed bookings */}
                      {booking.status === 'COMPLETED' && booking.passengerConfirmed && (
                        <Button
                          variant="outlined"
                          color="primary"
                          onClick={() => handleReviewClick(booking)}
                          startIcon={<RateReviewRoundedIcon />}
                          sx={{ mt: 1 }}
                        >
                          Rate Passenger
                        </Button>
                      )}
                    </Stack>
                  </Box>
                )
              })
            )}

            {error && (
              <Alert severity="error" onClose={() => dispatch(clearRideErrors())}>
                {error}
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setOtpDialogOpen(false)
              setBookings([])
              setOtpInputs({})
              setOtpSentBookings(new Set())
              dispatch(fetchMyRides())
            }}
            disabled={bookings.length > 0}
          >
            {bookings.length > 0 ? 'Complete All Verifications First' : 'Close'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Cancel Ride Dialog */}
      <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Cancel Ride</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => dispatch(clearRideErrors())}>
                {error}
              </Alert>
            )}
            {rideToCancel && (
              <>
                <Typography variant="body1">
                  Are you sure you want to cancel this ride?
                </Typography>
                <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                  <Stack spacing={1}>
                    <Typography variant="body2">
                      <strong>Route:</strong> {rideToCancel.source} → {rideToCancel.destination}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Date:</strong> {formatDate(rideToCancel.rideDate)} at {formatTime(rideToCancel.rideTime)}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Vehicle:</strong> {rideToCancel.vehicleModel} ({rideToCancel.vehicleLicensePlate})
                    </Typography>
                  </Stack>
                </Box>
                <Alert severity="warning">
                  <Typography variant="body2" fontWeight={600}>
                    Warning:
                  </Typography>
                  <Typography variant="body2">
                    • All passengers with confirmed bookings will be notified and their bookings will be cancelled
                  </Typography>
                  <Typography variant="body2">
                    • This action cannot be undone
                  </Typography>
                </Alert>
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialogOpen(false)} disabled={status === 'loading'}>
            Keep Ride
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={confirmCancelRide}
            disabled={status === 'loading'}
            startIcon={<CancelRoundedIcon />}
          >
            {status === 'loading' ? 'Cancelling...' : 'Cancel Ride'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reschedule Ride Dialog */}
      <RescheduleDialog
        open={rescheduleDialogOpen}
        onClose={() => {
          setRescheduleDialogOpen(false)
          setRideToReschedule(null)
        }}
        ride={rideToReschedule}
        onConfirm={confirmRescheduleRide}
        loading={status === 'loading'}
        error={error}
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
        title="Rate Passenger"
        subtitle={bookingToReview ? `Rate your experience with the passenger for this ride` : ''}
      />
    </PageContainer>
  )
}

export default MyRidesPage

