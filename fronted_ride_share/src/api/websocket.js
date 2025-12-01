import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

import config from '../config'
import { secureStorage, AUTH_STORAGE_KEY } from '../utils/secureStorage'

/**
 * WebSocket Client for Real-Time Notifications
 * Manages WebSocket connection to receive real-time updates
 */
class WebSocketClient {
  constructor() {
    this.client = null
    this.isConnected = false
    this.subscriptions = new Map()
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.listeners = new Set()
  }

  /**
   * Connect to WebSocket server
   * @param {string} userId - User ID for subscribing to user-specific topics
   * @param {string} userRole - User role (DRIVER or PASSENGER)
   */
  connect(userId, userRole) {
    if (this.isConnected || (this.client && this.client.connected)) {
      console.log('WebSocket already connected')
      return
    }

    if (!userId) {
      console.warn('Cannot connect WebSocket: User ID is required')
      return
    }

    // Get WebSocket URL - connect directly to ride-service
    // In production, this should be configured via environment variable
    const wsUrl = import.meta.env.VITE_WS_URL || 'http://localhost:8082/ws'
    
    console.log('🔌 Connecting to WebSocket:', wsUrl)

    // Create STOMP client with SockJS
    this.client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        // Only log in development
        if (import.meta.env.DEV) {
          console.log('STOMP:', str)
        }
      },
      onConnect: () => {
        console.log('✅ WebSocket connected successfully')
        this.isConnected = true
        this.reconnectAttempts = 0
        
        // Subscribe to user-specific topics based on role
        this.subscribeToTopics(userId, userRole)
        
        // Notify all listeners
        this.notifyListeners('connected')
      },
      onStompError: (frame) => {
        console.error('❌ STOMP error:', frame)
        this.isConnected = false
        this.notifyListeners('error', frame)
      },
      onWebSocketClose: () => {
        console.log('🔌 WebSocket closed')
        this.isConnected = false
        this.subscriptions.clear()
        this.notifyListeners('disconnected')
        
        // Attempt to reconnect if not manually disconnected
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++
          console.log(`🔄 Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
          setTimeout(() => {
            if (userId) {
              this.connect(userId, userRole)
            }
          }, this.reconnectDelay)
        } else {
          console.error('❌ Max reconnection attempts reached')
        }
      },
      onDisconnect: () => {
        console.log('🔌 WebSocket disconnected')
        this.isConnected = false
        this.subscriptions.clear()
        this.notifyListeners('disconnected')
      },
    })

    // Get JWT token for authentication
    const authData = secureStorage.get(AUTH_STORAGE_KEY, null)
    if (authData && authData.token) {
      // Add token to connection headers
      this.client.configure({
        connectHeaders: {
          Authorization: `Bearer ${authData.token}`,
        },
      })
    }

    // Activate the client
    this.client.activate()
  }

  /**
   * Subscribe to user-specific topics
   * @param {string} userId - User ID
   * @param {string} userRole - User role
   */
  subscribeToTopics(userId, userRole) {
    if (!this.client || !this.client.connected) {
      console.warn('Cannot subscribe: WebSocket not connected')
      return
    }

    // Unsubscribe from existing topics to prevent duplicates
    this.subscriptions.forEach((subscription) => {
      subscription.unsubscribe()
    })
    this.subscriptions.clear()

    const driverTopic = `/topic/driver/${userId}`
    const passengerTopic = `/topic/passenger/${userId}`
    
    // Subscribe to driver notifications (if user is driver or can be driver)
    if (!this.subscriptions.has('driver')) {
      const driverSub = this.client.subscribe(driverTopic, (message) => {
        try {
          const notification = JSON.parse(message.body)
          console.log('📨 Received driver notification:', notification)
          this.notifyListeners('notification', notification)
        } catch (error) {
          console.error('Error parsing notification:', error)
        }
      })
      this.subscriptions.set('driver', driverSub)
      console.log('✅ Subscribed to driver topic:', driverTopic)
    }

    // Subscribe to passenger notifications (if user is passenger or can be passenger)
    if (!this.subscriptions.has('passenger')) {
      const passengerSub = this.client.subscribe(passengerTopic, (message) => {
        try {
          const notification = JSON.parse(message.body)
          console.log('📨 Received passenger notification:', notification)
          this.notifyListeners('notification', notification)
        } catch (error) {
          console.error('Error parsing notification:', error)
        }
      })
      this.subscriptions.set('passenger', passengerSub)
      console.log('✅ Subscribed to passenger topic:', passengerTopic)
    }
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect() {
    if (this.client) {
      console.log('🔌 Disconnecting WebSocket...')
      this.subscriptions.forEach((subscription) => {
        subscription.unsubscribe()
      })
      this.subscriptions.clear()
      this.client.deactivate()
      this.client = null
      this.isConnected = false
      this.notifyListeners('disconnected')
    }
  }

  /**
   * Add event listener
   * @param {Function} listener - Callback function
   */
  addListener(listener) {
    this.listeners.add(listener)
  }

  /**
   * Remove event listener
   * @param {Function} listener - Callback function
   */
  removeListener(listener) {
    this.listeners.delete(listener)
  }

  /**
   * Notify all listeners
   * @param {string} event - Event type
   * @param {*} data - Event data
   */
  notifyListeners(event, data) {
    this.listeners.forEach((listener) => {
      try {
        listener(event, data)
      } catch (error) {
        console.error('Error in WebSocket listener:', error)
      }
    })
  }

  /**
   * Get connection status
   * @returns {boolean} True if connected
   */
  getConnectionStatus() {
    return this.isConnected && this.client && this.client.connected
  }
}

// Create singleton instance
const webSocketClient = new WebSocketClient()

export default webSocketClient
