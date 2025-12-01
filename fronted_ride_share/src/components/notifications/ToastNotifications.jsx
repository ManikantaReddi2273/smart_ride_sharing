import { useEffect, useRef } from 'react'
import { useSnackbar } from 'notistack'
import { useSelector } from 'react-redux'

/**
 * Toast Notifications Component
 * Displays toast notifications for real-time events
 */
const ToastNotifications = () => {
  const { enqueueSnackbar } = useSnackbar()
  const { lastNotification } = useSelector((state) => state.notifications)
  const prevNotificationRef = useRef(null)

  useEffect(() => {
    if (lastNotification && lastNotification.id !== prevNotificationRef.current?.id) {
      const notification = lastNotification
      
      // Determine variant based on notification type
      let variant = 'info'
      let autoHideDuration = 4000
      
      switch (notification.type) {
        case 'NEW_BOOKING':
          variant = 'success'
          autoHideDuration = 5000
          break
        case 'BOOKING_CONFIRMED':
          variant = 'success'
          autoHideDuration = 5000
          break
        case 'RIDE_CANCELLED':
        case 'BOOKING_CANCELLED':
          variant = 'error'
          autoHideDuration = 6000
          break
        case 'RIDE_RESCHEDULED':
        case 'RIDE_REMINDER':
          variant = 'warning'
          autoHideDuration = 6000
          break
        case 'RIDE_COMPLETED':
        case 'RIDE_COMPLETION_REQUEST':
          variant = 'info'
          autoHideDuration = 5000
          break
        default:
          variant = 'info'
      }

      // Show toast notification
      enqueueSnackbar(notification.message, {
        variant,
        autoHideDuration,
        anchorOrigin: {
          vertical: 'top',
          horizontal: 'right',
        },
        preventDuplicate: true,
      })

      prevNotificationRef.current = notification
    }
  }, [lastNotification, enqueueSnackbar])

  return null
}

export default ToastNotifications
