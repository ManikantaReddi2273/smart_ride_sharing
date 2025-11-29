import { useEffect } from 'react'
import {
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded'
import EventRoundedIcon from '@mui/icons-material/EventRounded'
import PeopleRoundedIcon from '@mui/icons-material/PeopleRounded'
import { useDispatch, useSelector } from 'react-redux'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import LoadingOverlay from '../../components/common/LoadingOverlay'
import { fetchMyRides } from './rideSlice'

const MyRidesPage = () => {
  const dispatch = useDispatch()
  const { myRides, status } = useSelector((state) => state.rides)

  useEffect(() => {
    dispatch(fetchMyRides())
  }, [dispatch])

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A'
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
  }

  const formatTime = (timeString) => {
    if (!timeString) return 'N/A'
    // Handle both "HH:mm:ss" and "HH:mm" formats
    const [hours, minutes] = timeString.split(':')
    const hour = parseInt(hours, 10)
    const ampm = hour >= 12 ? 'PM' : 'AM'
    const displayHour = hour % 12 || 12
    return `${displayHour}:${minutes} ${ampm}`
  }

  const getStatusColor = (status) => {
    switch (status?.toUpperCase()) {
      case 'POSTED':
        return 'info'
      case 'CONFIRMED':
        return 'success'
      case 'IN_PROGRESS':
        return 'warning'
      case 'COMPLETED':
        return 'default'
      case 'CANCELLED':
        return 'error'
      default:
        return 'default'
    }
  }

  if (status === 'loading') {
    return <LoadingOverlay />
  }

  return (
    <PageContainer>
      <Card>
        <CardContent>
          <Stack direction="row" alignItems="center" spacing={2} mb={3}>
            <Box
              sx={{
                width: 48,
                height: 48,
                borderRadius: 2,
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'rgba(15, 139, 141, 0.12)',
                color: 'primary.main',
              }}
            >
              <DirectionsCarRoundedIcon />
            </Box>
            <div>
              <Typography variant="h6">My Rides</Typography>
              <Typography variant="body2" color="text.secondary">
                View and manage all your posted rides
              </Typography>
            </div>
          </Stack>

          {myRides && myRides.length === 0 ? (
            <EmptyState
              icon={<DirectionsCarRoundedIcon />}
              title="No rides posted yet"
              message="Start sharing your rides by posting your first ride!"
            />
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Route
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Date & Time
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Seats
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Vehicle
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" fontWeight={600}>
                        Status
                      </Typography>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {myRides?.map((ride) => (
                    <TableRow key={ride.id} hover>
                      <TableCell>
                        <Stack spacing={0.5}>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <LocationOnRoundedIcon fontSize="small" color="primary" />
                            <Typography variant="body2" fontWeight={500}>
                              {ride.source || 'N/A'}
                            </Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1} pl={3}>
                            <Typography variant="caption" color="text.secondary">
                              ↓
                            </Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <LocationOnRoundedIcon fontSize="small" color="error" />
                            <Typography variant="body2" fontWeight={500}>
                              {ride.destination || 'N/A'}
                            </Typography>
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.5}>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <EventRoundedIcon fontSize="small" color="action" />
                            <Typography variant="body2">{formatDate(ride.rideDate)}</Typography>
                          </Stack>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <AccessTimeRoundedIcon fontSize="small" color="action" />
                            <Typography variant="body2">{formatTime(ride.rideTime)}</Typography>
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <PeopleRoundedIcon fontSize="small" color="action" />
                          <Typography variant="body2">
                            {ride.availableSeats || 0} / {ride.totalSeats || 0}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {ride.vehicleModel || 'N/A'}
                        </Typography>
                        {ride.vehicleLicensePlate && (
                          <Typography variant="caption" color="text.secondary">
                            {ride.vehicleLicensePlate}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={ride.status || 'POSTED'}
                          color={getStatusColor(ride.status)}
                          size="small"
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  )
}

export default MyRidesPage

