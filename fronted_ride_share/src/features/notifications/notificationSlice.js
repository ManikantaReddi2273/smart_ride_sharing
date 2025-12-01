import { createSlice } from '@reduxjs/toolkit'

const initialState = {
  notifications: [],
  unreadCount: 0,
  isConnected: false,
  lastNotification: null,
}

const notificationSlice = createSlice({
  name: 'notifications',
  initialState,
  reducers: {
    addNotification: (state, action) => {
      const notification = {
        ...action.payload,
        id: action.payload.id || `${action.payload.type}_${action.payload.rideId || action.payload.bookingId}_${action.payload.timestamp || Date.now()}`,
        read: false,
        timestamp: action.payload.timestamp || Date.now(),
      }
      
      // Check for duplicates based on type, rideId/bookingId, and timestamp (within 5 seconds)
      const isDuplicate = state.notifications.some((existing) => {
        if (existing.type === notification.type) {
          // Same type - check if it's the same event
          const sameRide = existing.rideId && notification.rideId && existing.rideId === notification.rideId
          const sameBooking = existing.bookingId && notification.bookingId && existing.bookingId === notification.bookingId
          const timeDiff = Math.abs((existing.timestamp || 0) - (notification.timestamp || 0))
          
          // Consider duplicate if same type, same ride/booking, and within 5 seconds
          if ((sameRide || sameBooking) && timeDiff < 5000) {
            return true
          }
        }
        return false
      })
      
      if (isDuplicate) {
        console.log('⚠️ Duplicate notification detected, skipping:', notification)
        return // Don't add duplicate
      }
      
      state.notifications.unshift(notification) // Add to beginning
      state.unreadCount += 1
      state.lastNotification = notification
      
      // Keep only last 50 notifications
      if (state.notifications.length > 50) {
        state.notifications = state.notifications.slice(0, 50)
      }
    },
    markAsRead: (state, action) => {
      const notificationId = action.payload
      const notification = state.notifications.find((n) => n.id === notificationId)
      if (notification && !notification.read) {
        notification.read = true
        state.unreadCount = Math.max(0, state.unreadCount - 1)
      }
    },
    markAllAsRead: (state) => {
      state.notifications.forEach((notification) => {
        notification.read = true
      })
      state.unreadCount = 0
    },
    removeNotification: (state, action) => {
      const notificationId = action.payload
      const notification = state.notifications.find((n) => n.id === notificationId)
      if (notification && !notification.read) {
        state.unreadCount = Math.max(0, state.unreadCount - 1)
      }
      state.notifications = state.notifications.filter((n) => n.id !== notificationId)
    },
    clearNotifications: (state) => {
      state.notifications = []
      state.unreadCount = 0
      state.lastNotification = null
    },
    setConnectionStatus: (state, action) => {
      state.isConnected = action.payload
    },
  },
})

export const {
  addNotification,
  markAsRead,
  markAllAsRead,
  removeNotification,
  clearNotifications,
  setConnectionStatus,
} = notificationSlice.actions

export default notificationSlice.reducer
