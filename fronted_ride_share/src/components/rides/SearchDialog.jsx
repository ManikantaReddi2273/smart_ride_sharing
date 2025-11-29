import { useState, useEffect } from 'react'
import PropTypes from 'prop-types'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  DialogActions,
  FormControl,
  FormHelperText,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Slider,
  Stack,
  TextField,
  Typography,
  Divider,
  Alert,
  CircularProgress,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListRoundedIcon from '@mui/icons-material/FilterListRounded'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useDispatch, useSelector } from 'react-redux'

import EmptyState from '../common/EmptyState'
import RideCard from './RideCard'
import { bookRide, searchRides, clearRideErrors } from '../../features/rides/rideSlice'
import { getAddressSuggestions } from '../../api/apiClient'

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

const schema = yup.object().shape({
  source: yup.string().required('Source is required'),
  destination: yup.string().required('Destination is required'),
  date: yup.string().required('Date is required'),
  minPrice: yup
    .number()
    .nullable()
    .transform((value, originalValue) => {
      // Convert empty string to null
      if (originalValue === '' || originalValue === null || originalValue === undefined) {
        return null
      }
      return isNaN(value) ? null : Number(value)
    })
    .min(0, 'Minimum price must be 0 or greater'),
  maxPrice: yup
    .number()
    .nullable()
    .transform((value, originalValue) => {
      // Convert empty string to null
      if (originalValue === '' || originalValue === null || originalValue === undefined) {
        return null
      }
      return isNaN(value) ? null : Number(value)
    })
    .min(0, 'Maximum price must be 0 or greater'),
  vehicleType: yup.string().nullable(),
  minRating: yup
    .number()
    .nullable()
    .integer('Rating must be a whole number')
    .transform((value, originalValue) => {
      // Convert empty string to null
      if (originalValue === '' || originalValue === null || originalValue === undefined) {
        return null
      }
      return isNaN(value) ? null : Number(value)
    })
    .min(0, 'Rating must be between 0 and 5')
    .max(5, 'Rating must be between 0 and 5'),
})

const bookingSchema = yup.object().shape({
  seats: yup
    .number()
    .typeError('Enter a valid number')
    .min(1, 'Minimum 1 seat')
    .required('Number of seats is required'),
  notes: yup.string().max(160, 'Keep it under 160 characters'),
})

const SearchDialog = ({ open, onClose }) => {
  const dispatch = useDispatch()
  const { searchResults, status, lastSearchCriteria, error } = useSelector((state) => state.rides)
  const [selectedRide, setSelectedRide] = useState(null)
  const [filtersExpanded, setFiltersExpanded] = useState(false)
  const [bookingDialogOpen, setBookingDialogOpen] = useState(false)
  const [bookingSuccess, setBookingSuccess] = useState(false)
  
  // Autocomplete state
  const [sourceSuggestions, setSourceSuggestions] = useState([])
  const [destinationSuggestions, setDestinationSuggestions] = useState([])
  const [loadingSourceSuggestions, setLoadingSourceSuggestions] = useState(false)
  const [loadingDestinationSuggestions, setLoadingDestinationSuggestions] = useState(false)
  const [sourceInputValue, setSourceInputValue] = useState('')
  const [destinationInputValue, setDestinationInputValue] = useState('')
  // CRITICAL: Store coordinates from autocomplete selection
  const [sourceCoordinates, setSourceCoordinates] = useState(null) // { latitude, longitude }
  const [destinationCoordinates, setDestinationCoordinates] = useState(null) // { latitude, longitude }

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      source: '',
      destination: '',
      date: '',
      minPrice: null,
      maxPrice: null,
      vehicleType: '',
      minRating: null,
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

  // Debounce source suggestions
  useEffect(() => {
    if (!sourceInputValue || sourceInputValue.trim().length < 2) {
      setSourceSuggestions([])
      return
    }

    const timeoutId = setTimeout(async () => {
      try {
        setLoadingSourceSuggestions(true)
        console.log('🔍 Fetching source suggestions for:', sourceInputValue.trim())
        const suggestions = await getAddressSuggestions(sourceInputValue.trim())
        console.log('✅ Source suggestions received:', suggestions?.length || 0, 'suggestions')
        setSourceSuggestions(suggestions || [])
      } catch (error) {
        console.error('❌ Failed to fetch source suggestions:', error)
        setSourceSuggestions([])
      } finally {
        setLoadingSourceSuggestions(false)
      }
    }, 300)
    return () => clearTimeout(timeoutId)
  }, [sourceInputValue])

  // Debounce destination suggestions
  useEffect(() => {
    if (!destinationInputValue || destinationInputValue.trim().length < 2) {
      setDestinationSuggestions([])
      return
    }

    const timeoutId = setTimeout(async () => {
      try {
        setLoadingDestinationSuggestions(true)
        console.log('🔍 Fetching destination suggestions for:', destinationInputValue.trim())
        const suggestions = await getAddressSuggestions(destinationInputValue.trim())
        console.log('✅ Destination suggestions received:', suggestions?.length || 0, 'suggestions')
        setDestinationSuggestions(suggestions || [])
      } catch (error) {
        console.error('❌ Failed to fetch destination suggestions:', error)
        setDestinationSuggestions([])
      } finally {
        setLoadingDestinationSuggestions(false)
      }
    }, 300)
    return () => clearTimeout(timeoutId)
  }, [destinationInputValue])

  // Reset search form when dialog opens/closes
  useEffect(() => {
    if (!open) {
      reset({
        source: '',
        destination: '',
        date: '',
        minPrice: null,
        maxPrice: null,
        vehicleType: '',
        minRating: null,
      })
      resetBooking({
        seats: 1,
        notes: '',
      })
      setSelectedRide(null)
      setBookingDialogOpen(false)
      setBookingSuccess(false)
      // Reset autocomplete state
      setSourceSuggestions([])
      setDestinationSuggestions([])
      setSourceInputValue('')
      setDestinationInputValue('')
      setSourceCoordinates(null)
      setDestinationCoordinates(null)
      dispatch(clearRideErrors())
    }
  }, [open, reset, resetBooking, dispatch])

  const onSearch = (values) => {
    if (!values.source || !values.source.trim()) return
    if (!values.destination || !values.destination.trim()) return
    if (!values.date) return

    const searchParams = {
      source: values.source.trim(),
      destination: values.destination.trim(),
      date: values.date,
    }

    // CRITICAL: Add coordinates if available (from autocomplete selection)
    // This enables intelligent route matching (passenger route anywhere along driver's journey)
    if (sourceCoordinates && sourceCoordinates.latitude != null && sourceCoordinates.longitude != null) {
      searchParams.sourceLatitude = sourceCoordinates.latitude
      searchParams.sourceLongitude = sourceCoordinates.longitude
      console.log('✅ Sending source coordinates for intelligent search:', sourceCoordinates)
    }

    if (destinationCoordinates && destinationCoordinates.latitude != null && destinationCoordinates.longitude != null) {
      searchParams.destinationLatitude = destinationCoordinates.latitude
      searchParams.destinationLongitude = destinationCoordinates.longitude
      console.log('✅ Sending destination coordinates for intelligent search:', destinationCoordinates)
    }

    // Only include optional filters if they have valid values
    if (values.minPrice !== null && values.minPrice !== undefined && values.minPrice !== '' && !isNaN(values.minPrice)) {
      searchParams.minPrice = Number(values.minPrice)
    }
    if (values.maxPrice !== null && values.maxPrice !== undefined && values.maxPrice !== '' && !isNaN(values.maxPrice)) {
      searchParams.maxPrice = Number(values.maxPrice)
    }
    if (values.vehicleType && values.vehicleType.trim() !== '') {
      searchParams.vehicleType = values.vehicleType.trim()
    }
    if (values.minRating !== null && values.minRating !== undefined && values.minRating !== '' && values.minRating !== 0 && !isNaN(values.minRating)) {
      searchParams.minRating = Math.floor(Number(values.minRating)) // Ensure integer value
    }

    console.log('📤 Searching rides with params:', searchParams)
    dispatch(searchRides(searchParams))
  }

  const onOpenBooking = (ride) => {
    setSelectedRide(ride)
    setBookingDialogOpen(true)
  }

  const onCloseBooking = () => {
    setSelectedRide(null)
    setBookingDialogOpen(false)
    resetBooking({
      seats: 1,
      notes: '',
    })
  }

  const onConfirmBooking = (values) => {
    if (!selectedRide) return
    setBookingSuccess(false)
    dispatch(clearRideErrors())
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
        setBookingSuccess(true)
        // Close booking dialog after 2 seconds and refresh search results
        setTimeout(() => {
          onCloseBooking()
          if (lastSearchCriteria) {
            dispatch(searchRides(lastSearchCriteria))
          }
        }, 2000)
      }
    })
  }

  const handleClose = () => {
    reset()
    onClose()
  }

  return (
    <>
      <Dialog
        open={open}
        onClose={handleClose}
        maxWidth="lg"
        fullWidth
        PaperProps={{
          sx: {
            maxHeight: '90vh',
            borderRadius: 2,
          },
        }}
      >
        <DialogTitle>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h5" fontWeight={700}>
              Search Rides & Book Seats
            </Typography>
            <IconButton onClick={handleClose} size="small">
              <CloseIcon />
            </IconButton>
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          <Box component="form" onSubmit={handleSubmit(onSearch)}>
            <Grid container spacing={2} mb={3}>
              <Grid item xs={12} md={4}>
                <Autocomplete
                  freeSolo
                  options={sourceSuggestions}
                  getOptionLabel={(option) => {
                    if (typeof option === 'string') return option
                    return option.label || option.name || ''
                  }}
                  getOptionKey={(option, index) => {
                    if (typeof option === 'string') return option
                    return option.latitude && option.longitude 
                      ? `${option.latitude}-${option.longitude}-${index}`
                      : `${option.label || option.name || index}-${index}`
                  }}
                  loading={loadingSourceSuggestions}
                  inputValue={sourceInputValue}
                  onInputChange={(event, newValue, reason) => {
                    setSourceInputValue(newValue || '')
                    if (!newValue || newValue.trim() === '') {
                      setSourceCoordinates(null)
                      setSourceSuggestions([])
                    }
                  }}
                  filterOptions={(x) => x} // Disable built-in filtering since we're using API
                  noOptionsText="No locations found"
                  loadingText="Loading suggestions..."
                  disablePortal={false} // Ensure dropdown appears correctly in Dialog
                  openOnFocus={true} // Open dropdown when input is focused
                  onChange={(event, newValue) => {
                    if (newValue && typeof newValue === 'object') {
                      // CRITICAL: Validate it's an India location before accepting
                      const countryCode = newValue.countryCode || ''
                      const country = newValue.country || ''
                      
                      if (countryCode && countryCode !== 'IN' && countryCode !== '') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', countryCode)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      if (country && !country.toLowerCase().includes('india') && countryCode !== 'IN') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', country)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      const selectedLabel = newValue.label || newValue.name || ''
                      
                      // CRITICAL: Store coordinates from autocomplete selection
                      if (newValue.latitude != null && newValue.longitude != null) {
                        setSourceCoordinates({
                          latitude: newValue.latitude,
                          longitude: newValue.longitude,
                        })
                        console.log('✅ Valid India location selected with coordinates:', selectedLabel, 
                          `[lat=${newValue.latitude}, lon=${newValue.longitude}]`)
                      } else {
                        console.warn('⚠️ Selected location missing coordinates:', selectedLabel)
                        setSourceCoordinates(null)
                      }
                      
                      setValue('source', selectedLabel)
                      setSourceInputValue(selectedLabel)
                    } else {
                      setSourceCoordinates(null)
                      setValue('source', newValue || '')
                      setSourceInputValue(newValue || '')
                    }
                    setSourceSuggestions([])
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      {...register('source')}
                      label="Source *"
                      required
                      error={!!errors.source}
                      helperText={errors.source?.message || 'Start typing to see suggestions'}
                      placeholder="Start typing to see suggestions"
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <Autocomplete
                  freeSolo
                  options={destinationSuggestions}
                  getOptionLabel={(option) => {
                    if (typeof option === 'string') return option
                    return option.label || option.name || ''
                  }}
                  getOptionKey={(option, index) => {
                    if (typeof option === 'string') return option
                    return option.latitude && option.longitude 
                      ? `${option.latitude}-${option.longitude}-${index}`
                      : `${option.label || option.name || index}-${index}`
                  }}
                  loading={loadingDestinationSuggestions}
                  inputValue={destinationInputValue}
                  onInputChange={(event, newValue, reason) => {
                    setDestinationInputValue(newValue || '')
                    if (!newValue || newValue.trim() === '') {
                      setDestinationCoordinates(null)
                      setDestinationSuggestions([])
                    }
                  }}
                  filterOptions={(x) => x} // Disable built-in filtering since we're using API
                  noOptionsText="No locations found"
                  loadingText="Loading suggestions..."
                  disablePortal={false} // Ensure dropdown appears correctly in Dialog
                  openOnFocus={true} // Open dropdown when input is focused
                  onChange={(event, newValue) => {
                    if (newValue && typeof newValue === 'object') {
                      // CRITICAL: Validate it's an India location before accepting
                      const countryCode = newValue.countryCode || ''
                      const country = newValue.country || ''
                      
                      if (countryCode && countryCode !== 'IN' && countryCode !== '') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', countryCode)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      if (country && !country.toLowerCase().includes('india') && countryCode !== 'IN') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', country)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      const selectedLabel = newValue.label || newValue.name || ''
                      
                      // CRITICAL: Store coordinates from autocomplete selection
                      if (newValue.latitude != null && newValue.longitude != null) {
                        setDestinationCoordinates({
                          latitude: newValue.latitude,
                          longitude: newValue.longitude,
                        })
                        console.log('✅ Valid India location selected with coordinates:', selectedLabel, 
                          `[lat=${newValue.latitude}, lon=${newValue.longitude}]`)
                      } else {
                        console.warn('⚠️ Selected location missing coordinates:', selectedLabel)
                        setDestinationCoordinates(null)
                      }
                      
                      setValue('destination', selectedLabel)
                      setDestinationInputValue(selectedLabel)
                    } else {
                      setDestinationCoordinates(null)
                      setValue('destination', newValue || '')
                      setDestinationInputValue(newValue || '')
                    }
                    setDestinationSuggestions([])
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      {...register('destination')}
                      label="Destination *"
                      required
                      error={!!errors.destination}
                      helperText={errors.destination?.message || 'Start typing to see suggestions'}
                      placeholder="Start typing to see suggestions"
                    />
                  )}
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
                  <Typography variant="subtitle1" fontWeight={600}>
                    Filters
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails>
                <Grid container spacing={3}>
                  <Grid item xs={12} md={6}>
                    <Typography variant="body2" mb={1} fontWeight={500}>
                      Price Range
                    </Typography>
                    <Stack direction="row" spacing={2}>
                      <TextField
                        label="Min Price (Optional)"
                        type="number"
                        fullWidth
                        {...register('minPrice')}
                        error={!!errors.minPrice}
                        helperText={errors.minPrice?.message || 'Leave empty if not needed'}
                        inputProps={{ min: 0 }}
                      />
                      <TextField
                        label="Max Price (Optional)"
                        type="number"
                        fullWidth
                        {...register('maxPrice')}
                        error={!!errors.maxPrice}
                        helperText={errors.maxPrice?.message || 'Leave empty if not needed'}
                        inputProps={{ min: 0 }}
                      />
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControl fullWidth>
                      <InputLabel>Vehicle Type (Optional)</InputLabel>
                      <Select
                        label="Vehicle Type (Optional)"
                        {...register('vehicleType')}
                        defaultValue=""
                      >
                        <MenuItem value="">All Vehicles</MenuItem>
                        <MenuItem value="Sedan">Sedan</MenuItem>
                        <MenuItem value="SUV">SUV</MenuItem>
                        <MenuItem value="Hatchback">Hatchback</MenuItem>
                        <MenuItem value="Coupe">Coupe</MenuItem>
                        <MenuItem value="Van">Van</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography variant="body2" mb={1} fontWeight={500}>
                      Minimum Driver Rating (Optional): {minRating || 0} ⭐
                    </Typography>
                    <Slider
                      value={minRating || 0}
                      onChange={(e, value) => setValue('minRating', value)}
                      min={0}
                      max={5}
                      step={1}
                      marks
                      valueLabelDisplay="auto"
                    />
                  </Grid>
                </Grid>
              </AccordionDetails>
            </Accordion>

            <Stack direction="row" spacing={2} mt={3}>
              <Button
                type="submit"
                variant="contained"
                size="large"
                startIcon={<SearchRoundedIcon />}
                disabled={status === 'loading'}
                sx={{ minWidth: 150 }}
              >
                {status === 'loading' ? (
                  <>
                    <CircularProgress size={20} sx={{ mr: 1 }} />
                    Searching...
                  </>
                ) : (
                  'Search Rides'
                )}
              </Button>
              <Button variant="outlined" onClick={handleClose}>
                Close
              </Button>
            </Stack>
          </Box>

          {error && status === 'failed' && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {error}
            </Alert>
          )}

          <Divider sx={{ my: 3 }} />

          {status === 'loading' && searchResults.length === 0 && (
            <Box display="flex" justifyContent="center" py={4}>
              <CircularProgress />
            </Box>
          )}

          {status === 'succeeded' && searchResults.length === 0 && (
            <EmptyState
              title="No rides found"
              message="Try adjusting your search criteria or filters"
            />
          )}

          {searchResults.length > 0 && (
            <Box>
              <Typography variant="h6" fontWeight={600} mb={2}>
                Search Results ({searchResults.length} rides found)
              </Typography>
              <Stack spacing={2}>
                {searchResults.map((ride) => (
                  <RideCard key={ride.id} ride={ride} onBookRide={onOpenBooking} />
                ))}
              </Stack>
            </Box>
          )}
        </DialogContent>
      </Dialog>

      {/* Booking Dialog */}
      <Dialog open={bookingDialogOpen} onClose={onCloseBooking} fullWidth maxWidth="sm">
        <DialogTitle>
          <Typography variant="h6" fontWeight={700}>
            Book Seat
          </Typography>
        </DialogTitle>
        <Box component="form" onSubmit={handleBookingSubmit(onConfirmBooking)}>
          <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 2 }}>
            {bookingSuccess && (
              <Alert severity="success" sx={{ mb: 1 }}>
                Booking confirmed successfully! Your seat has been reserved.
              </Alert>
            )}
            {error && status === 'failed' && !bookingSuccess && (
              <Alert severity="error" sx={{ mb: 1 }}>
                {error}
              </Alert>
            )}
            
            {/* Ride Summary */}
            {selectedRide && (
              <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2, mb: 1 }}>
                <Typography variant="subtitle1" fontWeight={600} mb={1}>
                  {selectedRide.source} → {selectedRide.destination}
                </Typography>
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Date & Time: {formatDate(selectedRide.rideDate || selectedRide.date)} at{' '}
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
                </Stack>
              </Box>
            )}

            <Divider />

            <Typography variant="subtitle2" fontWeight={600} color="primary" mb={1}>
              Booking Details
            </Typography>

            {/* Booking Form - Only Seats and Notes */}
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <TextField
                  label="Number of Seats"
                  type="number"
                  fullWidth
                  required
                  {...registerBooking('seats')}
                  error={!!bookingErrors.seats}
                  helperText={bookingErrors.seats?.message || `Maximum ${selectedRide?.availableSeats ?? 1} seats available`}
                  inputProps={{ min: 1, max: selectedRide?.availableSeats ?? 1 }}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Special Requests / Notes (Optional)"
                  multiline
                  minRows={3}
                  fullWidth
                  {...registerBooking('notes')}
                  error={!!bookingErrors.notes}
                  helperText={bookingErrors.notes?.message || 'Any special requests or pickup instructions...'}
                  placeholder="E.g., Pickup location details, luggage information, etc."
                />
              </Grid>
            </Grid>
            
            <Alert severity="info" sx={{ mt: 2 }}>
              Your booking confirmation and ride details will be sent to your registered email address.
            </Alert>
          </DialogContent>
          <Box sx={{ px: 3, pb: 3, pt: 2, display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
            <Button onClick={onCloseBooking} variant="outlined">
              Cancel
            </Button>
            <Button 
              type="submit" 
              variant="contained" 
              disabled={status === 'loading'}
              size="large"
            >
              {status === 'loading' ? (
                <>
                  <CircularProgress size={20} sx={{ mr: 1 }} />
                  Booking...
                </>
              ) : (
                'Confirm Booking'
              )}
            </Button>
          </Box>
        </Box>
      </Dialog>
    </>
  )
}

SearchDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
}

export default SearchDialog

