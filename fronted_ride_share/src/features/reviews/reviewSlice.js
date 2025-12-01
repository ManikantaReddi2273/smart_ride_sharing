import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'

// Async thunks
export const createPassengerReview = createAsyncThunk(
  'reviews/createPassengerReview',
  async ({ bookingId, rating, comment }, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.reviews.passenger(bookingId), {
        bookingId,
        rating,
        comment,
      })
      return data
    } catch (error) {
      return rejectWithValue(error.response?.data || error.message)
    }
  }
)

export const createDriverReview = createAsyncThunk(
  'reviews/createDriverReview',
  async ({ bookingId, rating, comment }, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.reviews.driver(bookingId), {
        bookingId,
        rating,
        comment,
      })
      return data
    } catch (error) {
      return rejectWithValue(error.response?.data || error.message)
    }
  }
)

export const getUserRating = createAsyncThunk(
  'reviews/getUserRating',
  async (userId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.reviews.userRating(userId))
      return data
    } catch (error) {
      return rejectWithValue(error.response?.data || error.message)
    }
  }
)

export const checkUserReviewed = createAsyncThunk(
  'reviews/checkUserReviewed',
  async (bookingId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.reviews.check(bookingId))
      console.log('Review check API response:', { bookingId, hasReviewed: data.hasReviewed })
      // Ensure we return a boolean, not undefined
      return data.hasReviewed === true
    } catch (error) {
      console.error('Error checking review status:', error)
      // Return false on error so user can still review (might be network issue)
      return false
    }
  }
)

const initialState = {
  loading: false,
  error: null,
  userRatings: {}, // userId -> rating data
  reviewedBookings: {}, // bookingId -> hasReviewed boolean
}

const reviewSlice = createSlice({
  name: 'reviews',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null
    },
    setReviewed: (state, action) => {
      const { bookingId, hasReviewed } = action.payload
      state.reviewedBookings[bookingId] = hasReviewed
    },
  },
  extraReducers: (builder) => {
    builder
      // Create passenger review
      .addCase(createPassengerReview.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(createPassengerReview.fulfilled, (state, action) => {
        state.loading = false
        if (action.payload.bookingId) {
          state.reviewedBookings[action.payload.bookingId] = true
        }
      })
      .addCase(createPassengerReview.rejected, (state, action) => {
        state.loading = false
        state.error = action.payload
      })
      // Create driver review
      .addCase(createDriverReview.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(createDriverReview.fulfilled, (state, action) => {
        state.loading = false
        if (action.payload.bookingId) {
          state.reviewedBookings[action.payload.bookingId] = true
        }
      })
      .addCase(createDriverReview.rejected, (state, action) => {
        state.loading = false
        state.error = action.payload
      })
      // Get user rating
      .addCase(getUserRating.fulfilled, (state, action) => {
        if (action.payload.userId) {
          state.userRatings[action.payload.userId] = action.payload
        }
      })
      // Check user reviewed
      .addCase(checkUserReviewed.fulfilled, (state, action) => {
        // This will be set via setReviewed reducer
      })
  },
})

export const { clearError, setReviewed } = reviewSlice.actions
export default reviewSlice.reducer
