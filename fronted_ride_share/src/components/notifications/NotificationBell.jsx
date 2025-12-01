import { useState } from 'react'
import { Badge, IconButton, Popover, Box, Typography, Button, Divider } from '@mui/material'
import { Notifications as NotificationsIcon, Close as CloseIcon } from '@mui/icons-material'
import { useDispatch, useSelector } from 'react-redux'

import { markAsRead, markAllAsRead, removeNotification } from '../../features/notifications/notificationSlice'
import NotificationItem from './NotificationItem'

/**
 * Notification Bell Component
 * Displays notification icon with badge and dropdown
 */
const NotificationBell = () => {
  const dispatch = useDispatch()
  const { notifications, unreadCount, isConnected } = useSelector((state) => state.notifications)
  const [anchorEl, setAnchorEl] = useState(null)

  const handleClick = (event) => {
    setAnchorEl(event.currentTarget)
  }

  const handleClose = () => {
    setAnchorEl(null)
  }

  const handleMarkAsRead = (notificationId) => {
    dispatch(markAsRead(notificationId))
  }

  const handleMarkAllAsRead = () => {
    dispatch(markAllAsRead())
  }

  const handleRemove = (notificationId) => {
    dispatch(removeNotification(notificationId))
  }

  const open = Boolean(anchorEl)
  const id = open ? 'notification-popover' : undefined

  // Get notification icon color based on connection status
  const iconColor = isConnected ? 'primary' : 'disabled'

  return (
    <>
      <IconButton
        color={iconColor}
        onClick={handleClick}
        aria-describedby={id}
        sx={{ position: 'relative' }}
      >
        <Badge badgeContent={unreadCount} color="error" max={99}>
          <NotificationsIcon />
        </Badge>
        {!isConnected && (
          <Box
            sx={{
              position: 'absolute',
              top: 8,
              right: 8,
              width: 8,
              height: 8,
              borderRadius: '50%',
              bgcolor: 'error.main',
            }}
          />
        )}
      </IconButton>

      <Popover
        id={id}
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        PaperProps={{
          sx: {
            width: 380,
            maxWidth: '90vw',
            maxHeight: 500,
            mt: 1,
          },
        }}
      >
        <Box sx={{ p: 2 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="h6" component="div">
              Notifications
            </Typography>
            <Box>
              {unreadCount > 0 && (
                <Button size="small" onClick={handleMarkAllAsRead} sx={{ mr: 1 }}>
                  Mark all read
                </Button>
              )}
              <IconButton size="small" onClick={handleClose}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Box>
          </Box>

          {!isConnected && (
            <Box
              sx={{
                p: 1,
                mb: 1,
                bgcolor: 'error.light',
                borderRadius: 1,
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
            >
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  bgcolor: 'error.main',
                  animation: 'pulse 2s infinite',
                  '@keyframes pulse': {
                    '0%, 100%': { opacity: 1 },
                    '50%': { opacity: 0.5 },
                  },
                }}
              />
              <Typography variant="caption" color="error">
                Reconnecting...
              </Typography>
            </Box>
          )}

          <Divider sx={{ mb: 1 }} />

          {notifications.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <NotificationsIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
              <Typography variant="body2" color="text.secondary">
                No notifications yet
              </Typography>
            </Box>
          ) : (
            <Box sx={{ maxHeight: 400, overflowY: 'auto' }}>
              {notifications.map((notification) => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  onMarkAsRead={handleMarkAsRead}
                  onRemove={handleRemove}
                />
              ))}
            </Box>
          )}
        </Box>
      </Popover>
    </>
  )
}

export default NotificationBell
