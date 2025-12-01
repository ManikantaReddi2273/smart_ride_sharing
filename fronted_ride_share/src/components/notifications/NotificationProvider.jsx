import { useEffect, useRef } from 'react'
import { useDispatch, useSelector } from 'react-redux'

import webSocketClient from '../../api/websocket'
import { addNotification, setConnectionStatus } from '../../features/notifications/notificationSlice'

/**
 * Notification Provider Component
 * Manages WebSocket connection and handles real-time notifications
 */
const NotificationProvider = ({ children }) => {
  const dispatch = useDispatch()
  const { token, user } = useSelector((state) => state.auth)
  const isConnectedRef = useRef(false)
  const listenerRef = useRef(null)
  const userIdRef = useRef(null)

  useEffect(() => {
    // Only connect if user is authenticated
    if (!token || !user || !user.id) {
      // Disconnect if user logs out
      if (isConnectedRef.current) {
        webSocketClient.disconnect()
        isConnectedRef.current = false
        userIdRef.current = null
        dispatch(setConnectionStatus(false))
      }
      return
    }

    // Prevent duplicate connections for the same user
    if (isConnectedRef.current && userIdRef.current === user.id) {
      console.log('🔌 WebSocket already connected for user:', user.id)
      return
    }

    // Disconnect previous connection if user changed
    if (isConnectedRef.current && userIdRef.current !== user.id) {
      console.log('🔌 User changed, disconnecting previous connection')
      webSocketClient.disconnect()
      isConnectedRef.current = false
      userIdRef.current = null
    }

    // Connect to WebSocket
    const connectWebSocket = () => {
      if (!isConnectedRef.current) {
        console.log('🔌 Initializing WebSocket connection for user:', user.id, user.role)
        
        // Remove old listener if exists
        if (listenerRef.current) {
          webSocketClient.removeListener(listenerRef.current)
        }
        
        // Add listener for notifications
        const handleNotification = (event, data) => {
          if (event === 'notification') {
            console.log('📨 Dispatching notification to Redux:', data)
            dispatch(addNotification(data))
          } else if (event === 'connected') {
            console.log('✅ WebSocket connected')
            isConnectedRef.current = true
            userIdRef.current = user.id
            dispatch(setConnectionStatus(true))
          } else if (event === 'disconnected') {
            console.log('🔌 WebSocket disconnected')
            isConnectedRef.current = false
            userIdRef.current = null
            dispatch(setConnectionStatus(false))
          } else if (event === 'error') {
            console.error('❌ WebSocket error:', data)
            isConnectedRef.current = false
            userIdRef.current = null
            dispatch(setConnectionStatus(false))
          }
        }

        listenerRef.current = handleNotification
        webSocketClient.addListener(handleNotification)

        // Connect with user ID and role
        webSocketClient.connect(user.id, user.role)
      }
    }

    // Small delay to ensure Redux state is ready
    const timeoutId = setTimeout(() => {
      connectWebSocket()
    }, 500)

    // Cleanup on unmount or when user changes
    return () => {
      clearTimeout(timeoutId)
      if (listenerRef.current) {
        webSocketClient.removeListener(listenerRef.current)
        listenerRef.current = null
      }
      // Only disconnect if user actually changed or component unmounting
      // Don't disconnect on every render
    }
  }, [token, user?.id, user?.role, dispatch])

  return <>{children}</>
}

export default NotificationProvider
