import { useState } from 'react'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Slider,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import TravelExploreRoundedIcon from '@mui/icons-material/TravelExploreRounded'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListRoundedIcon from '@mui/icons-material/FilterListRounded'
import AttachMoneyRoundedIcon from '@mui/icons-material/AttachMoneyRounded'
import StraightenRoundedIcon from '@mui/icons-material/StraightenRounded'
import { useDispatch, useSelector } from 'react-redux'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import LoadingOverlay from '../../components/common/LoadingOverlay'
import RideCard from '../../components/rides/RideCard'
import PaymentDialog from '../../components/payments/PaymentDialog'
import { bookRide, searchRides } from './rideSlice'
import { getPaymentOrderForRetry } from '../payments/paymentSlice'

const schema = yup.object().shape({
  source: yup.string().required('Source is required'),
  destination: yup.string().required('Destination is required'),
  date: yup.string().required('Date is required'),
  minPrice: yup.number().min(0, 'Minimum price must be 0 or greater'),
  maxPrice: yup.number().min(0, 'Maximum price must be 0 or greater'),
  vehicleType: yup.string(),
  minRating: yup.number().min(0, 'Rating must be between 0 and 5').max(5, 'Rating must be between 0 and 5'),
})

const bookingSchema = yup.object().shape({
  seats: yup
    .number()
    .typeError('Enter a valid number')
    .min(1, 'Minimum 1 seat')
    .required('Number of seats is required'),
  notes: yup.string().max(160, 'Keep it under 160 characters'),
})

const RideSearchPage = () => {
  const dispatch = useDispatch()
  const { searchResults, status, lastSearchCriteria, error } = useSelector((state) => state.rides)
  const [selectedRide, setSelectedRide] = useState(null)
  const [filtersExpanded, setFiltersExpanded] = useState(false)
  const [paymentDialogOpen, setPaymentDialogOpen] = useState(false)
  const [createdBooking, setCreatedBooking] = useState(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      source: '',
      destination: '',
      date: '',
      minPrice: '',
      maxPrice: '',
      vehicleType: '',
      minRating: '',
    },
  })

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

  const minRating = watch('minRating')

  const onSearch = (values) => {
    console.log('Search form submitted with values:', values)
    
    // Validate required fields
    if (!values.source || !values.source.trim()) {
      return
    }
    if (!values.destination || !values.destination.trim()) {
      return
    }
    if (!values.date) {
      return
    }
    
    const searchParams = {
      source: values.source.trim(),
      destination: values.destination.trim(),
      date: values.date,
    }
    
    // Add optional filters only if they have values
    if (values.minPrice && values.minPrice !== '') {
      searchParams.minPrice = Number(values.minPrice)
    }
    if (values.maxPrice && values.maxPrice !== '') {
      searchParams.maxPrice = Number(values.maxPrice)
    }
    if (values.vehicleType && values.vehicleType.trim() !== '') {
      searchParams.vehicleType = values.vehicleType.trim()
    }
    if (values.minRating && values.minRating !== '' && values.minRating !== 0) {
      searchParams.minRating = Number(values.minRating)
    }
    
    console.log('Dispatching search with params:', searchParams)
    dispatch(searchRides(searchParams))
  }

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
        const bookingResponse = action.payload
        
        console.log('✅ Booking created successfully:', bookingResponse)
        console.log('📦 Payment Order:', bookingResponse.paymentOrder)
        console.log('💰 Payment ID:', bookingResponse.paymentId)
        
        // Check if payment order is present
        const paymentOrder = bookingResponse.paymentOrder
        const orderId = paymentOrder?.orderId ?? paymentOrder?.order_id
        const hasValidPaymentOrder = paymentOrder && orderId
        
        if (hasValidPaymentOrder) {
          console.log('✅ Payment order found, opening payment dialog...')
          
          // Normalize paymentOrder keys to camelCase for consistency
          const normalizedPaymentOrder = {
            paymentId: paymentOrder.paymentId || paymentOrder.payment_id || bookingResponse.paymentId,
            orderId: paymentOrder.orderId || paymentOrder.order_id,
            amount: paymentOrder.amount,
            currency: paymentOrder.currency || 'INR',
            keyId: paymentOrder.keyId || paymentOrder.key_id,
            bookingId: paymentOrder.bookingId || paymentOrder.booking_id || bookingResponse.id,
          }
          
          // Update booking response with normalized payment order
          const updatedBooking = {
            ...bookingResponse,
            paymentOrder: normalizedPaymentOrder,
          }
          
          // Set booking and close booking dialog
          setCreatedBooking(updatedBooking)
          onCloseBooking()
          
          // Open payment dialog with a small delay to ensure booking dialog closes first
          setTimeout(() => {
            console.log('🚀 Opening payment dialog now...')
            setPaymentDialogOpen(true)
            console.log('✅ Payment dialog opened for booking:', updatedBooking.id)
          }, 100)
        } else {
          // No payment order - try to fetch it if paymentId exists
          if (bookingResponse.paymentId) {
            console.log('🔄 Payment ID found, attempting to fetch payment order...')
            dispatch(getPaymentOrderForRetry(bookingResponse.paymentId))
              .then((paymentOrderAction) => {
                if (paymentOrderAction.type.endsWith('fulfilled')) {
                  const fetchedPaymentOrder = paymentOrderAction.payload
                  console.log('✅ Payment order fetched:', fetchedPaymentOrder)
                  
                  const updatedBooking = {
                    ...bookingResponse,
                    paymentOrder: fetchedPaymentOrder,
                  }
                  
                  setCreatedBooking(updatedBooking)
                  onCloseBooking()
                  
                  setTimeout(() => {
                    console.log('🚀 Opening payment dialog after fetching order...')
                    setPaymentDialogOpen(true)
                    console.log('✅ Payment dialog opened after fetching order for booking:', updatedBooking.id)
                  }, 100)
                } else {
                  console.error('❌ Failed to fetch payment order:', paymentOrderAction.payload)
                  // Close booking dialog and refresh search results
                  onCloseBooking()
                  if (lastSearchCriteria) {
                    dispatch(searchRides(lastSearchCriteria))
                  }
                }
              })
              .catch((error) => {
                console.error('❌ Error fetching payment order:', error)
                // Close booking dialog and refresh search results
                onCloseBooking()
                if (lastSearchCriteria) {
                  dispatch(searchRides(lastSearchCriteria))
                }
              })
          } else {
            // No payment information - close booking dialog and refresh
            console.warn('⚠️ No payment information in booking response')
            onCloseBooking()
            if (lastSearchCriteria) {
              dispatch(searchRides(lastSearchCriteria))
            }
          }
        }
      }
    })
  }
  
  const handlePaymentSuccess = () => {
    console.log('✅ Payment successful, closing dialogs and refreshing...')
    setPaymentDialogOpen(false)
    setCreatedBooking(null)
    // Refresh search results to update available seats
    if (lastSearchCriteria) {
      dispatch(searchRides(lastSearchCriteria))
    }
  }

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A'
    try {
      // Handle both date string and Date object
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
      // Handle both "HH:mm:ss" and "HH:mm" formats
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

  // Debug logging
  console.log('RideSearchPage render - searchResults:', searchResults)
  console.log('RideSearchPage render - searchResults type:', typeof searchResults)
  console.log('RideSearchPage render - searchResults isArray:', Array.isArray(searchResults))
  console.log('RideSearchPage render - searchResults length:', Array.isArray(searchResults) ? searchResults.length : 'N/A')
  console.log('RideSearchPage render - lastSearchCriteria:', lastSearchCriteria)
  console.log('RideSearchPage render - status:', status)
  console.log('RideSearchPage render - error:', error)

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
              <TravelExploreRoundedIcon />
            </Box>
            <div>
              <Typography variant="h6">Find a ride</Typography>
              <Typography variant="body2" color="text.secondary">
                Search by route and date to find available rides
              </Typography>
            </div>
          </Stack>

          <Box component="form" onSubmit={handleSubmit(onSearch)}>
            <Grid container spacing={2} mb={2}>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Source"
                  fullWidth
                  required
                  {...register('source')}
                  error={!!errors.source}
                  helperText={errors.source?.message}
                  placeholder="Enter source location"
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Destination"
                  fullWidth
                  required
                  {...register('destination')}
                  error={!!errors.destination}
                  helperText={errors.destination?.message}
                  placeholder="Enter destination location"
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Date"
                  type="date"
                  fullWidth
                  required
                  InputLabelProps={{ shrink: true }}
                  {...register('date')}
                  error={!!errors.date}
                  helperText={errors.date?.message}
                  inputProps={{ min: new Date().toISOString().split('T')[0] }}
                />
              </Grid>
            </Grid>

            <Accordion expanded={filtersExpanded} onChange={() => setFiltersExpanded(!filtersExpanded)}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <FilterListRoundedIcon />
                  <Typography variant="subtitle2">Advanced Filters</Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Min Price (₹)"
                      type="number"
                      fullWidth
                      {...register('minPrice')}
                      error={!!errors.minPrice}
                      helperText={errors.minPrice?.message}
                      inputProps={{ min: 0 }}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Max Price (₹)"
                      type="number"
                      fullWidth
                      {...register('maxPrice')}
                      error={!!errors.maxPrice}
                      helperText={errors.maxPrice?.message}
                      inputProps={{ min: 0 }}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControl fullWidth>
                      <InputLabel>Vehicle Type</InputLabel>
                      <Select
                        label="Vehicle Type"
                        {...register('vehicleType')}
                        defaultValue=""
                      >
                        <MenuItem value="">All Types</MenuItem>
                        <MenuItem value="Sedan">Sedan</MenuItem>
                        <MenuItem value="SUV">SUV</MenuItem>
                        <MenuItem value="Hatchback">Hatchback</MenuItem>
                        <MenuItem value="Coupe">Coupe</MenuItem>
                        <MenuItem value="Van">Van</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Box>
                      <Typography variant="body2" gutterBottom>
                        Minimum Driver Rating: {minRating || 0} ⭐
                      </Typography>
                      <Slider
                        value={minRating ? Number(minRating) : 0}
                        onChange={(_, value) => setValue('minRating', value)}
                        min={0}
                        max={5}
                        step={0.5}
                        marks
                        valueLabelDisplay="auto"
                        valueLabelFormat={(value) => `${value} ⭐`}
                      />
                    </Box>
                  </Grid>
                </Grid>
              </AccordionDetails>
            </Accordion>

            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              sx={{ mt: 2 }}
              disabled={status === 'loading'}
              startIcon={status === 'loading' ? <TravelExploreRoundedIcon /> : null}
            >
              {status === 'loading' ? 'Searching...' : 'Search Rides'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      {status === 'loading' && (
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Stack direction="row" spacing={2} alignItems="center" justifyContent="center" sx={{ py: 4 }}>
              <CircularProgress size={24} />
              <Typography variant="body1" color="text.secondary">
                Searching for rides...
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      )}

      {error && status === 'failed' && (
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography color="error" variant="body1">
              Error: {error}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Please check your search criteria and try again.
            </Typography>
          </CardContent>
        </Card>
      )}

      {(lastSearchCriteria || (searchResults && searchResults.length > 0)) && (
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
              <Box>
                <Typography variant="h6">Search Results</Typography>
                {lastSearchCriteria && (
                  <Typography variant="body2" color="text.secondary">
                    {lastSearchCriteria.source} → {lastSearchCriteria.destination}
                    {lastSearchCriteria.date && ` on ${formatDate(lastSearchCriteria.date)}`}
                  </Typography>
                )}
              </Box>
              <Chip 
                label={`${Array.isArray(searchResults) ? searchResults.length : 0} rides found`} 
                color="primary" 
              />
            </Stack>

            {!searchResults || !Array.isArray(searchResults) || searchResults.length === 0 ? (
              <EmptyState
                title="No rides found"
                description="Try adjusting your search criteria or filters."
                icon="🔍"
              />
            ) : (
              <Stack spacing={2}>
                {searchResults.map((ride) => (
                  <RideCard key={ride.id} ride={ride} onBookRide={onOpenBooking} />
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
      )}

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
                <Stack direction="row" spacing={2} alignItems="center" sx={{ mt: 1, mb: 1 }}>
                  {(selectedRide.totalFare || selectedRide.fare) && (
                    <Chip
                      icon={<AttachMoneyRoundedIcon />}
                      label={`₹${(selectedRide.totalFare || selectedRide.fare).toFixed(2)}`}
                      color="primary"
                      size="small"
                    />
                  )}
                  {selectedRide.distanceKm && (
                    <Chip
                      icon={<StraightenRoundedIcon />}
                      label={`${selectedRide.distanceKm.toFixed(1)} km`}
                      variant="outlined"
                      size="small"
                    />
                  )}
                  <Chip
                    label={`${selectedRide.availableSeats ?? 0} seats available`}
                    color={selectedRide.availableSeats > 0 ? 'success' : 'error'}
                    size="small"
                  />
                </Stack>
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

      {/* Payment Dialog - Opens automatically after booking confirmation */}
      <PaymentDialog
        open={paymentDialogOpen}
        onClose={() => {
          console.log('💳 Payment dialog closing...')
          setPaymentDialogOpen(false)
          setCreatedBooking(null)
        }}
        booking={createdBooking}
        paymentOrder={createdBooking?.paymentOrder}
        onPaymentSuccess={handlePaymentSuccess}
      />
    </PageContainer>
  )
}

export default RideSearchPage
