import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteRoundedIcon from '@mui/icons-material/DeleteRounded'
import { useDispatch, useSelector } from 'react-redux'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'

import PageContainer from '../../components/common/PageContainer'
import { fetchProfile } from '../auth/authSlice'
import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'

const profileSchema = yup.object().shape({
  name: yup.string().required('Name is required'),
  phone: yup.string().required('Phone number is required'),
})

const vehicleSchema = yup.object().shape({
  model: yup.string().required('Vehicle model is required'),
  licensePlate: yup.string().required('License plate is required'),
  color: yup.string().required('Color is required'),
  capacity: yup
    .number()
    .typeError('Capacity must be a number')
    .min(1, 'Minimum 1 seat')
    .max(10, 'Maximum 10 seats')
    .required('Capacity is required'),
  year: yup
    .number()
    .typeError('Year must be a number')
    .min(1900, 'Invalid year')
    .max(new Date().getFullYear() + 1, 'Year cannot be in the future')
    .required('Year is required'),
})

const ProfilePage = () => {
  const dispatch = useDispatch()
  const { user, status, error } = useSelector((state) => state.auth)
  const [vehicles, setVehicles] = useState([])
  const [vehicleDialogOpen, setVehicleDialogOpen] = useState(false)
  const [vehicleError, setVehicleError] = useState(null)

  const {
    register: registerProfile,
    handleSubmit: handleProfileSubmit,
    reset: resetProfile,
    formState: { errors: profileErrors },
  } = useForm({
    resolver: yupResolver(profileSchema),
    defaultValues: {
      name: '',
      phone: '',
    },
  })

  const {
    register: registerVehicle,
    handleSubmit: handleVehicleSubmit,
    reset: resetVehicle,
    formState: { errors: vehicleErrors },
  } = useForm({
    resolver: yupResolver(vehicleSchema),
    defaultValues: {
      model: '',
      licensePlate: '',
      color: '',
      capacity: 4,
      year: new Date().getFullYear(),
    },
  })

  const fetchVehicles = useCallback(async () => {
    try {
      const { data } = await apiClient.get(endpoints.vehicles.list)
      setVehicles(data || [])
    } catch (err) {
      console.error('Failed to fetch vehicles:', err)
      setVehicles([])
    }
  }, [])

  useEffect(() => {
    dispatch(fetchProfile())
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchVehicles()
  }, [dispatch, fetchVehicles])

  useEffect(() => {
    if (user) {
      resetProfile({
        name: user.name ?? '',
        phone: user.phone ?? '',
      })
    }
  }, [user, resetProfile])

  const onProfileSubmit = async (values) => {
    try {
      await apiClient.put(endpoints.auth.profile, values)
      dispatch(fetchProfile())
      // Show success message
    } catch (err) {
      console.error('Failed to update profile:', err)
    }
  }

  const onVehicleSubmit = async (values) => {
    try {
      setVehicleError(null)
      await apiClient.post(endpoints.vehicles.create, values)
      await fetchVehicles()
      setVehicleDialogOpen(false)
      resetVehicle()
    } catch (err) {
      setVehicleError(err?.response?.data?.message || 'Failed to add vehicle')
    }
  }

  const onDeleteVehicle = async () => {
    if (!window.confirm('Are you sure you want to delete this vehicle?')) return
    try {
      // Note: Backend might need DELETE endpoint
      // await apiClient.delete(`${endpoints.vehicles.list}/${vehicleId}`)
      await fetchVehicles()
    } catch (err) {
      console.error('Failed to delete vehicle:', err)
    }
  }

  return (
    <PageContainer>
      <Card sx={{ mb: 3 }}>
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
              <ManageAccountsRoundedIcon />
            </Box>
            <div>
              <Typography variant="h6">Profile & preferences</Typography>
              <Typography variant="body2" color="text.secondary">
                Keep your personal and vehicle details up to date.
              </Typography>
            </div>
          </Stack>

          {error && status === 'failed' && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {error}
            </Alert>
          )}

          <Box component="form" onSubmit={handleProfileSubmit(onProfileSubmit)}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <TextField
                  label="Full name"
                  fullWidth
                  {...registerProfile('name')}
                  error={!!profileErrors.name}
                  helperText={profileErrors.name?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Email address"
                  fullWidth
                  disabled
                  value={user?.email || ''}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Phone number"
                  fullWidth
                  {...registerProfile('phone')}
                  error={!!profileErrors.phone}
                  helperText={profileErrors.phone?.message}
                />
              </Grid>
            </Grid>

            <Button type="submit" variant="contained" size="large" sx={{ mt: 4 }}>
              Save changes
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
            <Stack direction="row" alignItems="center" spacing={2}>
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
                <Typography variant="h6">My Vehicles</Typography>
                <Typography variant="body2" color="text.secondary">
                  Add vehicles to post rides. You need at least one vehicle to post a ride.
                </Typography>
              </div>
            </Stack>
            <Button
              variant="contained"
              startIcon={<AddRoundedIcon />}
              onClick={() => setVehicleDialogOpen(true)}
            >
              Add Vehicle
            </Button>
          </Stack>

          {vehicles.length === 0 ? (
            <Alert severity="info">
              No vehicles added yet. Add a vehicle to start posting rides.
            </Alert>
          ) : (
            <Grid container spacing={2}>
              {vehicles.map((vehicle) => (
                <Grid item xs={12} md={6} key={vehicle.id}>
                  <Card variant="outlined">
                    <CardContent>
                      <Stack direction="row" justifyContent="space-between" alignItems="start">
                        <Box>
                          <Typography variant="h6" fontWeight={600}>
                            {vehicle.model}
                          </Typography>
                          <Typography variant="body2" color="text.secondary" mt={0.5}>
                            License: {vehicle.licensePlate}
                          </Typography>
                          <Stack direction="row" spacing={1} mt={1}>
                            <Chip label={`${vehicle.capacity} seats`} size="small" />
                            <Chip label={vehicle.color} size="small" variant="outlined" />
                            {vehicle.year && (
                              <Chip label={vehicle.year} size="small" variant="outlined" />
                            )}
                          </Stack>
                        </Box>
                        <IconButton
                          size="small"
                          color="error"
                          onClick={onDeleteVehicle}
                        >
                          <DeleteRoundedIcon />
                        </IconButton>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
        </CardContent>
      </Card>

      <Dialog open={vehicleDialogOpen} onClose={() => setVehicleDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Vehicle</DialogTitle>
        <DialogContent>
          {vehicleError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {vehicleError}
            </Alert>
          )}
          <Box component="form" id="vehicle-form" onSubmit={handleVehicleSubmit(onVehicleSubmit)}>
            <Stack spacing={2} mt={1}>
              <TextField
                label="Vehicle model"
                fullWidth
                {...registerVehicle('model')}
                error={!!vehicleErrors.model}
                helperText={vehicleErrors.model?.message}
                placeholder="e.g., Toyota Camry"
              />
              <TextField
                label="License plate"
                fullWidth
                {...registerVehicle('licensePlate')}
                error={!!vehicleErrors.licensePlate}
                helperText={vehicleErrors.licensePlate?.message}
                placeholder="e.g., ABC123"
              />
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField
                    label="Color"
                    fullWidth
                    {...registerVehicle('color')}
                    error={!!vehicleErrors.color}
                    helperText={vehicleErrors.color?.message}
                    placeholder="e.g., Blue"
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    label="Year"
                    type="number"
                    fullWidth
                    {...registerVehicle('year')}
                    error={!!vehicleErrors.year}
                    helperText={vehicleErrors.year?.message}
                  />
                </Grid>
              </Grid>
              <TextField
                label="Seating capacity"
                type="number"
                fullWidth
                {...registerVehicle('capacity')}
                error={!!vehicleErrors.capacity}
                helperText={vehicleErrors.capacity?.message || 'Number of available seats'}
              />
            </Stack>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVehicleDialogOpen(false)}>Cancel</Button>
          <Button type="submit" form="vehicle-form" variant="contained">
            Add Vehicle
          </Button>
        </DialogActions>
      </Dialog>
    </PageContainer>
  )
}

export default ProfilePage
