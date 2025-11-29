import { useEffect, useState } from 'react'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import AddRoadRoundedIcon from '@mui/icons-material/AddRoadRounded'
import RouteRoundedIcon from '@mui/icons-material/RouteRounded'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import AttachMoneyRoundedIcon from '@mui/icons-material/AttachMoneyRounded'
import StraightenRoundedIcon from '@mui/icons-material/StraightenRounded'
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'

import PageContainer from '../../components/common/PageContainer'
import { createRide } from './rideSlice'
import { apiClient, calculateFare, getAddressSuggestions } from '../../api/apiClient'
import endpoints from '../../api/endpoints'

const schema = yup.object().shape({
  vehicleId: yup.number().required('Vehicle selection is required'),
  source: yup.string().required('Source is required'),
  destination: yup.string().required('Destination is required'),
  date: yup.string().required('Date is required'),
  time: yup.string().required('Time is required'),
  totalSeats: yup
    .number()
    .typeError('Seats must be a number')
    .min(2, 'Minimum 2 seats (driver + at least 1 passenger)')
    .required('Total seats is required'),
  notes: yup.string().max(1000, 'Notes must not exceed 1000 characters'),
})

const RidePostPage = () => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const { status, error } = useSelector((state) => state.rides)
  const [vehicles, setVehicles] = useState([])
  const [loadingVehicles, setLoadingVehicles] = useState(true)
  const [vehicleError, setVehicleError] = useState(null)
  const [farePreview, setFarePreview] = useState(null)
  const [isCalculatingFare, setIsCalculatingFare] = useState(false)
  const [fareError, setFareError] = useState(null)
  const [sourceSuggestions, setSourceSuggestions] = useState([])
  const [destinationSuggestions, setDestinationSuggestions] = useState([])
  const [loadingSourceSuggestions, setLoadingSourceSuggestions] = useState(false)
  const [loadingDestinationSuggestions, setLoadingDestinationSuggestions] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      vehicleId: '',
      source: '',
      destination: '',
      date: '',
      time: '',
      totalSeats: 4,
      notes: '',
    },
  })

  const selectedVehicleId = watch('vehicleId')
  const source = watch('source')
  const destination = watch('destination')
  const [sourceInputValue, setSourceInputValue] = useState('')
  const [destinationInputValue, setDestinationInputValue] = useState('')
  // CRITICAL: Store coordinates from autocomplete selection
  const [sourceCoordinates, setSourceCoordinates] = useState(null) // { latitude, longitude }
  const [destinationCoordinates, setDestinationCoordinates] = useState(null) // { latitude, longitude }

  useEffect(() => {
    fetchVehicles()
  }, [])

  // Debounce source suggestions
  useEffect(() => {
    if (!sourceInputValue || sourceInputValue.trim().length < 2) {
      setSourceSuggestions([])
      return
    }
    const timeoutId = setTimeout(async () => {
      try {
        setLoadingSourceSuggestions(true)
        const suggestions = await getAddressSuggestions(sourceInputValue)
        setSourceSuggestions(suggestions)
      } catch (error) {
        console.error('Failed to fetch source suggestions:', error)
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
        const suggestions = await getAddressSuggestions(destinationInputValue)
        setDestinationSuggestions(suggestions)
      } catch (error) {
        console.error('Failed to fetch destination suggestions:', error)
        setDestinationSuggestions([])
      } finally {
        setLoadingDestinationSuggestions(false)
      }
    }, 300)
    return () => clearTimeout(timeoutId)
  }, [destinationInputValue])

  // Calculate fare when source and destination change (including coordinates)
  useEffect(() => {
    if (!source || !destination || source.trim() === '' || destination.trim() === '') {
      setFarePreview(null)
      setFareError(null)
      return
    }

    // Debounce fare calculation
    const timeoutId = setTimeout(() => {
      // CRITICAL: Pass coordinates if available (from autocomplete selection)
      // This ensures 100% accurate distance calculation
      handleCalculateFare(source.trim(), destination.trim())
    }, 800) // Wait 800ms after user stops typing

    return () => clearTimeout(timeoutId)
  }, [source, destination, sourceCoordinates, destinationCoordinates]) // Watch coordinates too!

  const fetchVehicles = async () => {
    try {
      setLoadingVehicles(true)
      setVehicleError(null)
      const { data } = await apiClient.get(endpoints.vehicles.list)
      setVehicles(data || [])
      if (!data || data.length === 0) {
        setVehicleError('No vehicles found. Please add a vehicle in your profile first.')
      }
    } catch (err) {
      console.error('Failed to fetch vehicles:', err)
      setVehicleError('Failed to load vehicles. Please try again.')
      setVehicles([])
    } finally {
      setLoadingVehicles(false)
    }
  }

  const handleCalculateFare = async (source, destination) => {
    try {
      setIsCalculatingFare(true)
      setFareError(null)
      
      // CRITICAL: Send coordinates if available (from autocomplete selection)
      // This ensures 100% accurate fare preview without geocoding errors
      console.log('🔍 Calculating fare with:', {
        source,
        destination,
        sourceCoordinates,
        destinationCoordinates,
        hasSourceCoords: !!(sourceCoordinates && sourceCoordinates.latitude),
        hasDestCoords: !!(destinationCoordinates && destinationCoordinates.latitude)
      })
      
      const fareData = await calculateFare(
        source, 
        destination, 
        sourceCoordinates,  // Pass coordinates if available
        destinationCoordinates
      )
      
      console.log('✅ Fare calculated:', {
        distance: fareData.distanceKm,
        fare: fareData.totalFare,
        usedCoordinates: !!(sourceCoordinates && destinationCoordinates)
      })
      
      setFarePreview(fareData)
    } catch (error) {
      console.error('Failed to calculate fare:', error)
      // Extract error message from response
      const errorMessage = error?.response?.data?.message || 
                          error?.response?.data?.error || 
                          error?.message || 
                          'Failed to calculate fare. Please check your locations and try again.'
      setFareError(errorMessage)
      setFarePreview(null)
    } finally {
      setIsCalculatingFare(false)
    }
  }


  const selectedVehicle = vehicles.find((v) => v.id === Number(selectedVehicleId))

  const onSubmit = (values) => {
    // CRITICAL: Send coordinates if available (from autocomplete selection)
    // This ensures 100% accurate distance calculation without geocoding errors
    const payload = {
      vehicleId: Number(values.vehicleId),
      source: values.source,
      destination: values.destination,
      rideDate: values.date,
      rideTime: values.time,
      totalSeats: Number(values.totalSeats),
      notes: values.notes || '',
    }
    
    // Add coordinates if available (from autocomplete selection)
    if (sourceCoordinates && sourceCoordinates.latitude != null && sourceCoordinates.longitude != null) {
      payload.sourceLatitude = sourceCoordinates.latitude
      payload.sourceLongitude = sourceCoordinates.longitude
      console.log('✅ Sending source coordinates:', sourceCoordinates)
    } else {
      console.warn('⚠️ Source coordinates not available - backend will geocode')
    }
    
    if (destinationCoordinates && destinationCoordinates.latitude != null && destinationCoordinates.longitude != null) {
      payload.destinationLatitude = destinationCoordinates.latitude
      payload.destinationLongitude = destinationCoordinates.longitude
      console.log('✅ Sending destination coordinates:', destinationCoordinates)
    } else {
      console.warn('⚠️ Destination coordinates not available - backend will geocode')
    }
    
    console.log('📤 Sending ride post request with payload:', payload)
    
    dispatch(createRide(payload)).then((action) => {
      if (action.type.endsWith('fulfilled')) {
        reset()
        // Clear coordinates on success
        setSourceCoordinates(null)
        setDestinationCoordinates(null)
        navigate('/dashboard')
      }
    })
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
              <RouteRoundedIcon />
            </Box>
            <div>
              <Typography variant="h6">Post a new ride</Typography>
              <Typography variant="body2" color="text.secondary">
                Share route details to open seats for passengers.
              </Typography>
            </div>
          </Stack>

          {error && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {error}
            </Alert>
          )}

          {vehicleError && vehicles.length === 0 && (
            <Alert 
              severity="warning" 
              sx={{ mb: 3 }}
              action={
                <Button color="inherit" size="small" onClick={() => navigate('/profile')}>
                  Add Vehicle
                </Button>
              }
            >
              {vehicleError}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <FormControl fullWidth error={!!errors.vehicleId}>
                  <InputLabel>Select Vehicle</InputLabel>
                  <Select
                    label="Select Vehicle"
                    {...register('vehicleId')}
                    disabled={loadingVehicles || vehicles.length === 0}
                    value={selectedVehicleId || ''}
                  >
                    {vehicles.map((vehicle) => (
                      <MenuItem key={vehicle.id} value={vehicle.id}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <DirectionsCarRoundedIcon fontSize="small" />
                          <Box>
                            <Typography variant="body1">{vehicle.model}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {vehicle.licensePlate} • {vehicle.capacity} seats
                            </Typography>
                          </Box>
                        </Stack>
                      </MenuItem>
                    ))}
                  </Select>
                  {errors.vehicleId && (
                    <FormHelperText>{errors.vehicleId.message}</FormHelperText>
                  )}
                  {!errors.vehicleId && selectedVehicle && (
                    <FormHelperText>
                      Selected: {selectedVehicle.model} ({selectedVehicle.capacity} seats max)
                    </FormHelperText>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <Autocomplete
                  freeSolo
                  options={sourceSuggestions}
                  getOptionLabel={(option) => {
                    if (typeof option === 'string') return option
                    return option.label || option.name || ''
                  }}
                  getOptionKey={(option, index) => {
                    if (typeof option === 'string') return option
                    // Use coordinates as unique key to avoid React warnings
                    return option.latitude && option.longitude 
                      ? `${option.latitude}-${option.longitude}-${index}`
                      : `${option.label || option.name || index}-${index}`
                  }}
                  loading={loadingSourceSuggestions}
                  inputValue={sourceInputValue}
                  onInputChange={(event, newValue) => {
                    setSourceInputValue(newValue || '')
                  }}
                  onChange={(event, newValue) => {
                    if (newValue && typeof newValue === 'object') {
                      // CRITICAL: Validate it's an India location before accepting
                      const countryCode = newValue.countryCode || ''
                      const country = newValue.country || ''
                      
                      // Reject non-India locations
                      if (countryCode && countryCode !== 'IN' && countryCode !== '') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', countryCode)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      // Also check country name
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
                        setSourceCoordinates(null) // Clear coordinates if not available
                      }
                      
                      setValue('source', selectedLabel)
                      setSourceInputValue(selectedLabel)
                    } else {
                      // User typed manually or cleared - clear coordinates
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
                      label="Source"
                      error={!!errors.source}
                      helperText={errors.source?.message || 'Start typing to see suggestions'}
                      InputProps={{
                        ...params.InputProps,
                        endAdornment: (
                          <>
                            {loadingSourceSuggestions ? <CircularProgress color="inherit" size={20} /> : null}
                            {params.InputProps.endAdornment}
                          </>
                        ),
                      }}
                    />
                  )}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} key={option.label || option.name}>
                      <Stack spacing={0.5}>
                        <Typography variant="body1">{option.label || option.name}</Typography>
                        {(option.locality || option.region || option.country) && (
                          <Typography variant="caption" color="text.secondary">
                            {[option.locality, option.region, option.country].filter(Boolean).join(', ')}
                          </Typography>
                        )}
                      </Stack>
                    </Box>
                  )}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <Autocomplete
                  freeSolo
                  options={destinationSuggestions}
                  getOptionLabel={(option) => {
                    if (typeof option === 'string') return option
                    return option.label || option.name || ''
                  }}
                  getOptionKey={(option, index) => {
                    if (typeof option === 'string') return option
                    // Use coordinates as unique key to avoid React warnings
                    return option.latitude && option.longitude 
                      ? `${option.latitude}-${option.longitude}-${index}`
                      : `${option.label || option.name || index}-${index}`
                  }}
                  loading={loadingDestinationSuggestions}
                  inputValue={destinationInputValue}
                  onInputChange={(event, newValue) => {
                    setDestinationInputValue(newValue || '')
                    // Clear coordinates if user is typing manually (not selecting from autocomplete)
                    if (!newValue || newValue.trim() === '') {
                      setDestinationCoordinates(null)
                    }
                  }}
                  onChange={(event, newValue) => {
                    if (newValue && typeof newValue === 'object') {
                      // CRITICAL: Validate it's an India location before accepting
                      const countryCode = newValue.countryCode || ''
                      const country = newValue.country || ''
                      
                      // Reject non-India locations
                      if (countryCode && countryCode !== 'IN' && countryCode !== '') {
                        console.error('⚠️ Non-India location selected:', newValue.label, 'Country:', countryCode)
                        alert('Please select a location from India only.')
                        return
                      }
                      
                      // Also check country name
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
                        setDestinationCoordinates(null) // Clear coordinates if not available
                      }
                      
                      setValue('destination', selectedLabel)
                      setDestinationInputValue(selectedLabel)
                    } else {
                      // User typed manually or cleared - clear coordinates
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
                      label="Destination"
                      error={!!errors.destination}
                      helperText={errors.destination?.message || 'Start typing to see suggestions'}
                      InputProps={{
                        ...params.InputProps,
                        endAdornment: (
                          <>
                            {loadingDestinationSuggestions ? <CircularProgress color="inherit" size={20} /> : null}
                            {params.InputProps.endAdornment}
                          </>
                        ),
                      }}
                    />
                  )}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} key={option.label || option.name}>
                      <Stack spacing={0.5}>
                        <Typography variant="body1">{option.label || option.name}</Typography>
                        {(option.locality || option.region || option.country) && (
                          <Typography variant="caption" color="text.secondary">
                            {[option.locality, option.region, option.country].filter(Boolean).join(', ')}
                          </Typography>
                        )}
                      </Stack>
                    </Box>
                  )}
                />
              </Grid>
              
              {/* Fare Preview */}
              {source && destination && source.trim() && destination.trim() && (
                <Grid item xs={12}>
                  <Card 
                    variant="outlined" 
                    sx={{ 
                      bgcolor: 'rgba(15, 139, 141, 0.04)',
                      borderColor: 'primary.light',
                    }}
                  >
                    <CardContent>
                      {isCalculatingFare ? (
                        <Stack direction="row" spacing={2} alignItems="center">
                          <CircularProgress size={20} />
                          <Typography variant="body2" color="text.secondary">
                            Calculating fare...
                          </Typography>
                        </Stack>
                      ) : fareError ? (
                        <Alert severity="warning" sx={{ py: 0 }}>
                          <Typography variant="body2">{fareError}</Typography>
                        </Alert>
                      ) : farePreview ? (
                        <Stack direction="row" spacing={3} alignItems="center" flexWrap="wrap">
                          <Stack direction="row" spacing={1} alignItems="center">
                            <AttachMoneyRoundedIcon color="primary" fontSize="small" />
                            <Typography variant="h6" fontWeight={700} color="primary">
                              ₹{farePreview.totalFare?.toFixed(2) || '0.00'}
                            </Typography>
                          </Stack>
                          {farePreview.distanceKm && (
                            <Stack direction="row" spacing={1} alignItems="center">
                              <StraightenRoundedIcon color="primary" fontSize="small" />
                              <Typography variant="body2" color="text.secondary">
                                {farePreview.distanceKm.toFixed(1)} km
                              </Typography>
                            </Stack>
                          )}
                          {farePreview.estimatedDurationText && (
                            <Stack direction="row" spacing={1} alignItems="center">
                              <AccessTimeRoundedIcon color="primary" fontSize="small" />
                              <Typography variant="body2" color="text.secondary">
                                {farePreview.estimatedDurationText}
                              </Typography>
                            </Stack>
                          )}
                          <Box flex={1} />
                          <Typography variant="caption" color="text.secondary">
                            Base: ₹{farePreview.baseFare?.toFixed(2) || '0.00'} + 
                            {' '}(₹{farePreview.ratePerKm?.toFixed(2) || '0.00'} × {farePreview.distanceKm?.toFixed(1) || '0'} km)
                          </Typography>
                        </Stack>
                      ) : null}
                    </CardContent>
                  </Card>
                </Grid>
              )}
              <Grid item xs={12} md={4}>
                <TextField
                  label="Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  fullWidth
                  {...register('date')}
                  error={!!errors.date}
                  helperText={errors.date?.message}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Time"
                  type="time"
                  InputLabelProps={{ shrink: true }}
                  fullWidth
                  {...register('time')}
                  error={!!errors.time}
                  helperText={errors.time?.message}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Total Seats"
                  type="number"
                  fullWidth
                  {...register('totalSeats')}
                  error={!!errors.totalSeats}
                  helperText={errors.totalSeats?.message || 'Including driver (minimum 2)'}
                  inputProps={{ min: 2, max: selectedVehicle?.capacity || 10 }}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Notes / Pickup instructions"
                  multiline
                  minRows={3}
                  fullWidth
                  {...register('notes')}
                  error={!!errors.notes}
                  helperText={errors.notes?.message}
                />
              </Grid>
            </Grid>

            <Button
              type="submit"
              variant="contained"
              startIcon={<AddRoadRoundedIcon />}
              size="large"
              sx={{ mt: 4 }}
              disabled={status === 'loading' || vehicles.length === 0 || loadingVehicles}
            >
              {status === 'loading' ? 'Publishing...' : 'Publish ride'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </PageContainer>
  )
}

export default RidePostPage

