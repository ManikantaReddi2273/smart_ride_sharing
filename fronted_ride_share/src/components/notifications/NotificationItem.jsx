import { Box, Typography, IconButton, Chip } from '@mui/material'
import { CheckCircle, Cancel, Schedule, CheckCircleOutline, Close, Notifications } from '@mui/icons-material'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

/**
 * Notification Item Component
 * Displays individual notification with appropriate icon and styling
 */
const NotificationItem = ({ notification, onMarkAsRead, onRemove }) => {
  const getNotificationIcon = () => {
    switch (notification.type) {
      case 'NEW_BOOKING':
        return <CheckCircleOutline color="success" />
      case 'BOOKING_CONFIRMED':
        return <CheckCircle color="success" />
      case 'RIDE_CANCELLED':
        return <Cancel color="error" />
      case 'RIDE_RESCHEDULED':
        return <Schedule color="warning" />
      case 'BOOKING_CANCELLED':
        return <Cancel color="error" />
      case 'RIDE_COMPLETED':
        return <CheckCircle color="info" />
      case 'RIDE_COMPLETION_REQUEST':
        return <Schedule color="info" />
      case 'RIDE_REMINDER':
        return <Notifications color="warning" />
      default:
        return <CheckCircleOutline color="primary" />
    }
  }

  const getNotificationColor = () => {
    switch (notification.type) {
      case 'NEW_BOOKING':
      case 'BOOKING_CONFIRMED':
        return 'success'
      case 'RIDE_CANCELLED':
      case 'BOOKING_CANCELLED':
        return 'error'
      case 'RIDE_RESCHEDULED':
      case 'RIDE_REMINDER':
        return 'warning'
      case 'RIDE_COMPLETED':
      case 'RIDE_COMPLETION_REQUEST':
        return 'info'
      default:
        return 'default'
    }
  }

  const handleMarkAsRead = () => {
    if (!notification.read && onMarkAsRead) {
      onMarkAsRead(notification.id)
    }
  }

  const handleRemove = () => {
    if (onRemove) {
      onRemove(notification.id)
    }
  }

  return (
    <Box
      sx={{
        p: 1.5,
        mb: 1,
        borderRadius: 1,
        bgcolor: notification.read ? 'background.paper' : 'action.hover',
        border: notification.read ? 'none' : '1px solid',
        borderColor: `${getNotificationColor()}.light`,
        cursor: 'pointer',
        '&:hover': {
          bgcolor: 'action.hover',
        },
      }}
      onClick={handleMarkAsRead}
    >
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
        <Box sx={{ mt: 0.5 }}>{getNotificationIcon()}</Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
            <Typography variant="body2" sx={{ fontWeight: notification.read ? 400 : 600 }}>
              {notification.message}
            </Typography>
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                handleRemove()
              }}
              sx={{ ml: 1 }}
            >
              <Close fontSize="small" />
            </IconButton>
          </Box>
          {notification.type && (
            <Chip
              label={notification.type.replace(/_/g, ' ')}
              size="small"
              color={getNotificationColor()}
              sx={{ height: 20, fontSize: '0.65rem', mr: 1 }}
            />
          )}
          {notification.timestamp && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              {dayjs(notification.timestamp).fromNow()}
            </Typography>
          )}
        </Box>
      </Box>
    </Box>
  )
}

export default NotificationItem
