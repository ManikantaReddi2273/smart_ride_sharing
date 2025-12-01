import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Alert,
  Box,
  Typography,
} from '@mui/material'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { TimePicker } from '@mui/x-date-pickers/TimePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs'
import dayjs from 'dayjs'

/**
 * Reschedule Dialog Component
 * Allows drivers to reschedule their rides (change date/time)
 */
const RescheduleDialog = ({ open, onClose, ride, onConfirm, loading, error }) => {
  const [newDate, setNewDate] = useState(null)
  const [newTime, setNewTime] = useState(null)

  useEffect(() => {
    if (ride && open) {
      // Initialize with current ride date/time
      if (ride.rideDate) {
        setNewDate(dayjs(ride.rideDate))
      }
      if (ride.rideTime) {
        const [hours, minutes] = ride.rideTime.split(':')
        setNewTime(dayjs().hour(parseInt(hours)).minute(parseInt(minutes)).second(0))
      }
    }
  }, [ride, open])

  const handleConfirm = () => {
    if (!newDate || !newTime) {
      return
    }

    const rescheduleData = {
      source: ride.source,
      destination: ride.destination,
      rideDate: newDate.format('YYYY-MM-DD'),
      rideTime: newTime.format('HH:mm:ss'),
      totalSeats: ride.totalSeats,
      vehicleId: ride.vehicleId,
      notes: ride.notes || '',
    }

    onConfirm(rescheduleData)
  }

  const handleClose = () => {
    setNewDate(null)
    setNewTime(null)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Reschedule Ride</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" onClose={() => {}}>
              {error}
            </Alert>
          )}

          <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              Current Schedule
            </Typography>
            <Typography variant="body1" fontWeight={600}>
              {ride?.source} → {ride?.destination}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Date: {ride?.rideDate ? dayjs(ride.rideDate).format('MMMM DD, YYYY') : 'N/A'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Time: {ride?.rideTime ? dayjs(`2000-01-01 ${ride.rideTime}`).format('hh:mm A') : 'N/A'}
            </Typography>
          </Box>

          <Alert severity="info">
            <Typography variant="body2" fontWeight={600}>
              Important:
            </Typography>
            <Typography variant="body2">
              • All passengers with confirmed bookings will be notified automatically
            </Typography>
            <Typography variant="body2">
              • You can only reschedule rides that are not completed or cancelled
            </Typography>
          </Alert>

          <LocalizationProvider dateAdapter={AdapterDayjs}>
            <DatePicker
              label="New Date *"
              value={newDate}
              onChange={(date) => setNewDate(date)}
              minDate={dayjs()}
              slotProps={{
                textField: {
                  fullWidth: true,
                  required: true,
                },
              }}
            />

            <TimePicker
              label="New Time *"
              value={newTime}
              onChange={(time) => setNewTime(time)}
              slotProps={{
                textField: {
                  fullWidth: true,
                  required: true,
                },
              }}
            />
          </LocalizationProvider>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color="primary"
          onClick={handleConfirm}
          disabled={!newDate || !newTime || loading}
        >
          {loading ? 'Rescheduling...' : 'Reschedule Ride'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default RescheduleDialog
