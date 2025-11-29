import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'
import { secureStorage, AUTH_STORAGE_KEY } from '../../utils/secureStorage'

const persistedAuth = secureStorage.get(AUTH_STORAGE_KEY, null)
const PENDING_VERIFICATION_KEY = 'ride-sharing/pending-verification-email'
const persistedPendingEmail = secureStorage.get(PENDING_VERIFICATION_KEY, null)

const initialState = {
  user: persistedAuth?.user ?? null,
  token: persistedAuth?.token ?? null,
  refreshToken: persistedAuth?.refreshToken ?? null,
  status: 'idle',
  error: null,
  initializing: false,
  pendingVerificationEmail: persistedPendingEmail,
  verificationStatus: 'idle',
  verificationMessage: null,
  verificationError: null,
  registrationMessage: null,
}

const handleError = (error) => {
  // Handle different error response formats
  if (error?.response?.data) {
    // Check for message field (most common)
    if (error.response.data.message) {
      return error.response.data.message
    }
    // Check for error field
    if (error.response.data.error) {
      return error.response.data.error
    }
    // Check if data is a string
    if (typeof error.response.data === 'string') {
      return error.response.data
    }
  }
  // Check network errors
  if (error?.message) {
    if (error.message.includes('Network Error') || error.message.includes('timeout')) {
      return 'Network error. Please check your connection and try again.'
    }
    return error.message
  }
  // Fallback to default
  return 'An unexpected error occurred. Please try again.'
}

export const registerUser = createAsyncThunk(
  'auth/register',
  async (payload, { rejectWithValue }) => {
    try {
      console.log('Register API call:', endpoints.auth.register, payload)
      const { data } = await apiClient.post(endpoints.auth.register, payload)
      console.log('Register API response:', data)
      return data
    } catch (error) {
      console.error('Register API error:', error)
      return rejectWithValue(handleError(error))
    }
  },
)

export const loginUser = createAsyncThunk('auth/login', async (payload, { rejectWithValue }) => {
  try {
    console.log('Login API call:', endpoints.auth.login, payload)
    const { data } = await apiClient.post(endpoints.auth.login, payload)
    console.log('Login API response:', data)
    return data
  } catch (error) {
    console.error('Login API error:', error)
    return rejectWithValue(handleError(error))
  }
})

export const verifyOtp = createAsyncThunk(
  'auth/verifyOtp',
  async (payload, { rejectWithValue }) => {
    try {
      console.log('Verifying OTP with payload:', { ...payload, otp: '***' })
      console.log('OTP endpoint:', endpoints.auth.verifyOtp)
      const { data } = await apiClient.post(endpoints.auth.verifyOtp, payload)
      console.log('OTP verification response:', data)
      return data
    } catch (error) {
      console.error('OTP verification error:', error)
      console.error('Error response:', error?.response)
      console.error('Error data:', error?.response?.data)
      return rejectWithValue(handleError(error))
    }
  },
)

export const fetchProfile = createAsyncThunk(
  'auth/profile',
  async (_, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.auth.profile)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  },
)

const persistAuth = (state) => {
  secureStorage.set(AUTH_STORAGE_KEY, {
    user: state.user,
    token: state.token,
    refreshToken: state.refreshToken,
  })
}

const clearAuth = () => {
  secureStorage.remove(AUTH_STORAGE_KEY)
}

const setPendingVerificationEmail = (email) => {
  if (email) {
    secureStorage.set(PENDING_VERIFICATION_KEY, email)
  } else {
    secureStorage.remove(PENDING_VERIFICATION_KEY)
  }
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    refreshTokenSuccess: (state, action) => {
      state.token = action.payload.accessToken
      state.refreshToken = action.payload.refreshToken ?? state.refreshToken
      persistAuth(state)
    },
    logout: (state) => {
      state.user = null
      state.token = null
      state.refreshToken = null
      state.status = 'idle'
      state.error = null
      state.pendingVerificationEmail = null
      state.verificationStatus = 'idle'
      state.verificationMessage = null
      state.verificationError = null
      state.registrationMessage = null
      clearAuth()
      setPendingVerificationEmail(null)
    },
    clearPendingVerification: (state) => {
      state.pendingVerificationEmail = null
      state.verificationStatus = 'idle'
      state.verificationMessage = null
      state.verificationError = null
      setPendingVerificationEmail(null)
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(registerUser.pending, (state) => {
        state.status = 'loading'
        state.error = null
        state.pendingVerificationEmail = null
        state.verificationStatus = 'idle'
        state.verificationMessage = null
        state.verificationError = null
        state.registrationMessage = null
        setPendingVerificationEmail(null)
      })
      .addCase(registerUser.fulfilled, (state, action) => {
        state.status = 'succeeded'
        const payload = action.payload || {}

        if (payload.verificationRequired) {
          state.pendingVerificationEmail = payload.email
          state.verificationStatus = 'pending'
          state.verificationMessage = payload.message
          state.registrationMessage = payload.message
          setPendingVerificationEmail(payload.email)
          state.user = null
          state.token = null
          state.refreshToken = null
          clearAuth()
        } else {
          state.user = {
            id: payload.userId,
            email: payload.email,
            name: payload.name,
            role: payload.role,
          }
          state.token = payload.token || payload.accessToken || null
          state.refreshToken = payload.refreshToken || null
          persistAuth(state)
          setPendingVerificationEmail(null)
        }
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
        state.pendingVerificationEmail = null
        state.verificationStatus = 'idle'
        state.verificationMessage = null
        state.verificationError = null
        state.registrationMessage = null
        setPendingVerificationEmail(null)
      })
      .addCase(loginUser.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Backend returns token, userId, email, name, role directly (not nested in user object)
        state.user = {
          id: action.payload.userId,
          email: action.payload.email,
          name: action.payload.name,
          role: action.payload.role,
        }
        state.token = action.payload.token || action.payload.accessToken
        state.refreshToken = action.payload.refreshToken || null
        persistAuth(state)
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(fetchProfile.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(fetchProfile.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.user = action.payload
        persistAuth(state)
      })
      .addCase(fetchProfile.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(verifyOtp.pending, (state) => {
        state.verificationStatus = 'loading'
        state.verificationError = null
      })
      .addCase(verifyOtp.fulfilled, (state, action) => {
        state.verificationStatus = 'succeeded'
        state.verificationMessage = action.payload?.message || 'Verification successful! You can now login with your credentials.'
        state.registrationMessage = action.payload?.message || 'Verification successful! You can now login with your credentials.'
        // Keep pendingVerificationEmail for redirect - will be cleared when user navigates away
      })
      .addCase(verifyOtp.rejected, (state, action) => {
        state.verificationStatus = 'failed'
        state.verificationError = action.payload
      })
  },
})

export const { refreshTokenSuccess, logout, clearPendingVerification } = authSlice.actions

export default authSlice.reducer

