import { useEffect } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import DirectionsCarFilledRoundedIcon from '@mui/icons-material/DirectionsCarFilledRounded'
import LoginRoundedIcon from '@mui/icons-material/LoginRounded'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useNavigate, useLocation } from 'react-router-dom'

import { loginUser } from './authSlice'

const schema = yup.object().shape({
  emailOrPhone: yup.string().required('Email or phone is required'),
  password: yup.string().min(6, 'Minimum 6 characters').required('Password is required'),
})

const LoginPage = () => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const { status, error, token } = useSelector((state) => state.auth)
  const successMessage = location.state?.message
  const preFilledEmail = location.state?.email

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      emailOrPhone: preFilledEmail || '',
      password: '',
    },
  })

  // Auto-fill email if provided via navigation state
  useEffect(() => {
    if (preFilledEmail) {
      setValue('emailOrPhone', preFilledEmail)
    }
  }, [preFilledEmail, setValue])

  useEffect(() => {
    if (token) {
      navigate('/dashboard', { replace: true })
    }
  }, [token, navigate])

  const onSubmit = (values) => {
    try {
      console.log('Logging in with:', { emailOrPhone: values.emailOrPhone, password: '***' })
      dispatch(loginUser(values))
    } catch (err) {
      console.error('Login form error:', err)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        backgroundColor: '#E8F8F5',
        p: 3,
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 500 }}>
        <CardContent sx={{ p: 4 }}>
          <Stack spacing={1} mb={4} alignItems="center" textAlign="center">
            <Box
              sx={{
                width: 64,
                height: 64,
                borderRadius: 3,
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'rgba(15, 139, 141, 0.12)',
                color: 'primary.main',
              }}
            >
              <DirectionsCarFilledRoundedIcon fontSize="large" />
            </Box>
            <Typography variant="h4" fontWeight={700}>
              Smart Ride Sharing
            </Typography>
            <Typography color="text.secondary">
              Sign in to manage your rides and bookings
            </Typography>
          </Stack>

          {successMessage && (
            <Alert severity="success" sx={{ mb: 3 }}>
              {successMessage}
            </Alert>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {error}
            </Alert>
          )}

          <Stack component="form" spacing={3} onSubmit={handleSubmit(onSubmit)}>
            <TextField
              label="Email or Phone"
              {...register('emailOrPhone')}
              error={!!errors.emailOrPhone}
              helperText={errors.emailOrPhone?.message || 'Enter your email address or phone number'}
              fullWidth
            />
            <TextField
              label="Password"
              type="password"
              {...register('password')}
              error={!!errors.password}
              helperText={errors.password?.message}
              fullWidth
            />
            <Button
              type="submit"
              variant="contained"
              size="large"
              startIcon={<LoginRoundedIcon />}
              disabled={status === 'loading'}
            >
              {status === 'loading' ? 'Signing in...' : 'Sign in'}
            </Button>
          </Stack>

          <Typography textAlign="center" mt={3} variant="body2">
            New to the platform?{' '}
            <Link to="/register" style={{ color: '#0F8B8D', fontWeight: 600 }}>
              Create an account
            </Link>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  )
}

export default LoginPage

