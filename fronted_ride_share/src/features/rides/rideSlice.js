import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'

const handleError = (error) =>
  error?.response?.data?.message || error?.message || 'Unexpected error'

export const fetchMyRides = createAsyncThunk('rides/fetchMyRides', async (_, { rejectWithValue }) => {
  try {
    const { data } = await apiClient.get(endpoints.rides.myRides)
    return data
  } catch (error) {
    return rejectWithValue(handleError(error))
  }
})

export const fetchMyBookings = createAsyncThunk(
  'rides/fetchMyBookings',
  async (_, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.rides.myBookings)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

export const searchRides = createAsyncThunk(
  'rides/search',
  async (criteria, { rejectWithValue }) => {
    try {
      // Format date to YYYY-MM-DD if provided
      const params = {
        source: criteria.source,
        destination: criteria.destination,
      }
      
      // CRITICAL: Add coordinates if available (from autocomplete selection)
      // This enables intelligent route matching (passenger route anywhere along driver's journey)
      if (criteria.sourceLatitude != null && criteria.sourceLongitude != null) {
        params.sourceLatitude = criteria.sourceLatitude
        params.sourceLongitude = criteria.sourceLongitude
        console.log('✅ Adding source coordinates to search:', { lat: criteria.sourceLatitude, lon: criteria.sourceLongitude })
      }
      
      if (criteria.destinationLatitude != null && criteria.destinationLongitude != null) {
        params.destinationLatitude = criteria.destinationLatitude
        params.destinationLongitude = criteria.destinationLongitude
        console.log('✅ Adding destination coordinates to search:', { lat: criteria.destinationLatitude, lon: criteria.destinationLongitude })
      }
      
      // Handle date - backend expects 'rideDate' parameter
      if (criteria.date) {
        // If date is already in YYYY-MM-DD format, use it directly
        if (typeof criteria.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(criteria.date)) {
          params.rideDate = criteria.date
        } else {
          // Try to parse and format the date
          const date = new Date(criteria.date)
          if (!isNaN(date.getTime())) {
            params.rideDate = date.toISOString().split('T')[0]
          } else {
            return rejectWithValue('Invalid date format. Please use YYYY-MM-DD format.')
          }
        }
      } else {
        return rejectWithValue('Date is required')
      }
      
      // Add optional filters
      if (criteria.minPrice !== undefined && criteria.minPrice !== null && criteria.minPrice !== '') {
        params.minPrice = Number(criteria.minPrice)
      }
      if (criteria.maxPrice !== undefined && criteria.maxPrice !== null && criteria.maxPrice !== '') {
        params.maxPrice = Number(criteria.maxPrice)
      }
      if (criteria.vehicleType && criteria.vehicleType.trim() !== '') {
        params.vehicleType = criteria.vehicleType.trim()
      }
      if (criteria.minRating !== undefined && criteria.minRating !== null && criteria.minRating !== '') {
        params.minRating = Math.floor(Number(criteria.minRating)) // Ensure integer value
      }
      
      console.log('📤 Search API call with params:', params)
      console.log('Search endpoint:', endpoints.rides.search)
      
      const { data } = await apiClient.get(endpoints.rides.search, {
        params,
      })
      
      console.log('Search API response:', data)
      console.log('Response type:', typeof data)
      console.log('Is array:', Array.isArray(data))
      console.log('Number of results:', Array.isArray(data) ? data.length : 'Not an array')
      
      // Ensure we return an array
      const results = Array.isArray(data) ? data : []
      
      // Debug: Log driver ratings for each ride
      if (results.length > 0) {
        console.log('🔍 Driver ratings in search results:')
        results.forEach((ride, index) => {
          console.log(`  Ride ${index + 1}:`, {
            id: ride.id,
            driverId: ride.driverId,
            driverName: ride.driverName,
            driverRating: ride.driverRating,
            driverTotalReviews: ride.driverTotalReviews,
            ratingType: typeof ride.driverRating,
            ratingValue: ride.driverRating
          })
        })
      }
      
      return { results, criteria }
    } catch (error) {
      console.error('Search API error:', error)
      console.error('Error response:', error?.response)
      console.error('Error data:', error?.response?.data)
      return rejectWithValue(handleError(error))
    }
  },
)

export const createRide = createAsyncThunk('rides/create', async (payload, { rejectWithValue }) => {
  try {
    const { data } = await apiClient.post(endpoints.rides.base, payload)
    return data
  } catch (error) {
    return rejectWithValue(handleError(error))
  }
})

export const bookRide = createAsyncThunk(
  'rides/book',
  async ({ rideId, passengerDetails }, { rejectWithValue }) => {
    try {
      // Backend expects { seatsBooked: number, passengerSource?: string, passengerDestination?: string }
      // Passenger info comes from logged-in user account (from JWT token)
      const payload = {
        seatsBooked: Number(passengerDetails.seats) || 1,
      }
      
      // Add optional passenger route if provided
      if (passengerDetails.passengerSource) {
        payload.passengerSource = passengerDetails.passengerSource.trim()
      }
      if (passengerDetails.passengerDestination) {
        payload.passengerDestination = passengerDetails.passengerDestination.trim()
      }
      
      console.log('Booking ride with payload:', payload)
      console.log('Booking endpoint:', endpoints.rides.book(rideId))
      
      const { data } = await apiClient.post(endpoints.rides.book(rideId), payload)
      
      console.log('Booking API response:', data)
      console.log('Payment order details:', data.paymentOrder)
      
      return data
    } catch (error) {
      console.error('Booking API error:', error)
      console.error('Error response:', error?.response)
      return rejectWithValue(handleError(error))
    }
  },
)

export const verifyPayment = createAsyncThunk(
  'rides/verifyPayment',
  async ({ bookingId, paymentVerificationData }, { rejectWithValue }) => {
    try {
      console.log('Verifying payment for bookingId:', bookingId)
      console.log('Payment verification data:', paymentVerificationData)
      
      const { data } = await apiClient.post(
        endpoints.rides.verifyPayment(bookingId),
        paymentVerificationData
      )
      
      console.log('Payment verification response:', data)
      
      return data
    } catch (error) {
      console.error('Payment verification error:', error)
      console.error('Error response:', error?.response)
      return rejectWithValue(handleError(error))
    }
  },
)

export const updateRideStatus = createAsyncThunk(
  'rides/updateRideStatus',
  async ({ rideId, status }, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.put(endpoints.rides.status(rideId), null, {
        params: { status },
      })
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

export const verifyOtp = createAsyncThunk(
  'rides/verifyOtp',
  async ({ bookingId, otp }, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.rides.verifyOtp(bookingId), { otp })
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

export const sendOtpToPassenger = createAsyncThunk(
  'rides/sendOtpToPassenger',
  async (bookingId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.rides.sendOtp(bookingId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

export const updateRide = createAsyncThunk(
  'rides/updateRide',
  async ({ rideId, rideData }, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.put(endpoints.rides.update(rideId), rideData)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

export const cancelRide = createAsyncThunk(
  'rides/cancelRide',
  async (rideId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.delete(endpoints.rides.cancel(rideId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

const initialState = {
  myRides: [],
  myBookings: [],
  searchResults: [],
  lastSearchCriteria: null,
  status: 'idle',
  error: null,
}

const ridesSlice = createSlice({
  name: 'rides',
  initialState,
  reducers: {
    clearRideErrors: (state) => {
      state.error = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchMyRides.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(fetchMyRides.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.myRides = action.payload
      })
      .addCase(fetchMyRides.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(fetchMyBookings.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(fetchMyBookings.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.myBookings = action.payload
      })
      .addCase(fetchMyBookings.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(searchRides.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(searchRides.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.searchResults = action.payload.results
        state.lastSearchCriteria = action.payload.criteria
      })
      .addCase(searchRides.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(createRide.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(createRide.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.myRides = [action.payload, ...state.myRides]
      })
      .addCase(createRide.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(bookRide.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(bookRide.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.myBookings = [action.payload, ...state.myBookings]
      })
      .addCase(bookRide.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(verifyPayment.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(verifyPayment.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Update the booking in myBookings if it exists
        const bookingIndex = state.myBookings.findIndex(b => b.id === action.payload.id)
        if (bookingIndex !== -1) {
          state.myBookings[bookingIndex] = action.payload
        } else {
          state.myBookings = [action.payload, ...state.myBookings]
        }
      })
      .addCase(verifyPayment.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(updateRideStatus.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(updateRideStatus.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Update the ride in myRides array
        const rideIndex = state.myRides.findIndex((r) => r.id === action.payload.id)
        if (rideIndex !== -1) {
          state.myRides[rideIndex] = action.payload
        }
      })
      .addCase(updateRideStatus.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(verifyOtp.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(verifyOtp.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Refresh rides to update status
      })
      .addCase(verifyOtp.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(sendOtpToPassenger.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(sendOtpToPassenger.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // OTP sent successfully
      })
      .addCase(sendOtpToPassenger.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(updateRide.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(updateRide.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Update the ride in myRides array
        const rideIndex = state.myRides.findIndex((r) => r.id === action.payload.id)
        if (rideIndex !== -1) {
          state.myRides[rideIndex] = action.payload
        }
      })
      .addCase(updateRide.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(cancelRide.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(cancelRide.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Update the ride in myRides array
        const rideIndex = state.myRides.findIndex((r) => r.id === action.payload.id)
        if (rideIndex !== -1) {
          state.myRides[rideIndex] = action.payload
        }
      })
      .addCase(cancelRide.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
  },
})

export const { clearRideErrors } = ridesSlice.actions

export default ridesSlice.reducer

