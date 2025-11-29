import axios from 'axios'

import config from '../config'
import { refreshTokenSuccess, logout } from '../features/auth/authSlice'
import { secureStorage, AUTH_STORAGE_KEY } from '../utils/secureStorage'
import endpoints from './endpoints'

export const apiClient = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 45000,
})

/**
 * Calculate fare for a route
 * @param {string} source - Source location name
 * @param {string} destination - Destination location name
 * @param {Object} sourceCoords - Optional source coordinates { latitude, longitude }
 * @param {Object} destCoords - Optional destination coordinates { latitude, longitude }
 * @returns {Promise<Object>} Fare calculation response
 */
export const calculateFare = async (source, destination, sourceCoords = null, destCoords = null) => {
  try {
    const params = { source, destination }
    
    // CRITICAL: Add coordinates if available (from autocomplete selection)
    // This ensures 100% accurate distance calculation without geocoding errors
    if (sourceCoords && sourceCoords.latitude != null && sourceCoords.longitude != null) {
      params.sourceLat = sourceCoords.latitude
      params.sourceLon = sourceCoords.longitude
      console.log('✅ Adding source coordinates to API call:', { lat: sourceCoords.latitude, lon: sourceCoords.longitude })
    } else {
      console.warn('⚠️ Source coordinates not available - backend will geocode')
    }
    
    if (destCoords && destCoords.latitude != null && destCoords.longitude != null) {
      params.destLat = destCoords.latitude
      params.destLon = destCoords.longitude
      console.log('✅ Adding destination coordinates to API call:', { lat: destCoords.latitude, lon: destCoords.longitude })
    } else {
      console.warn('⚠️ Destination coordinates not available - backend will geocode')
    }
    
    console.log('📤 API Request params:', params)
    
    const { data } = await apiClient.get(endpoints.rides.calculateFare, { params })
    
    console.log('📥 API Response:', { distance: data.distanceKm, fare: data.totalFare })
    
    return data
  } catch (error) {
    console.error('❌ API Error:', error)
    throw error
  }
}

/**
 * Get address autocomplete suggestions
 * @param {string} query - Search query (minimum 2 characters)
 * @returns {Promise<Array>} Array of address suggestions
 */
export const getAddressSuggestions = async (query) => {
  try {
    if (!query || query.trim().length < 2) {
      return []
    }
    const { data } = await apiClient.get(endpoints.rides.addressSuggestions, {
      params: { query: query.trim() },
    })
    return data || []
  } catch (error) {
    console.error('Failed to fetch address suggestions:', error)
    return []
  }
}

export const setupInterceptors = (store) => {
  apiClient.interceptors.request.use((request) => {
    const { token } = store.getState().auth
    if (token) {
      request.headers.Authorization = `Bearer ${token}`
    }
    return request
  })

  let isRefreshing = false
  const queue = []

  const processQueue = (error, token = null) => {
    queue.forEach((prom) => {
      if (error) {
        prom.reject(error)
      } else {
        prom.resolve(token)
      }
    })
    queue.length = 0
  }

  apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config
      if (error.response?.status === 401 && !originalRequest._retry) {
        const { refreshToken } = store.getState().auth
        if (!refreshToken) {
          store.dispatch(logout())
          return Promise.reject(error)
        }

        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            queue.push({
              resolve: (token) => {
                originalRequest.headers.Authorization = `Bearer ${token}`
                resolve(apiClient(originalRequest))
              },
              reject,
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const { data } = await axios.post(
            `${config.apiBaseUrl}${endpoints.auth.refresh}`,
            { refreshToken },
          )
          store.dispatch(refreshTokenSuccess(data))
          secureStorage.set(AUTH_STORAGE_KEY, {
            ...store.getState().auth,
            token: data.accessToken,
            refreshToken: data.refreshToken ?? refreshToken,
          })
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
          processQueue(null, data.accessToken)
          return apiClient(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          store.dispatch(logout())
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }
      return Promise.reject(error)
    },
  )
}

