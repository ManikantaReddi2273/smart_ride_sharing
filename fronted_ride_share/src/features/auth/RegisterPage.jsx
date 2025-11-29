import { useEffect } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import PersonAddAltRoundedIcon from '@mui/icons-material/PersonAddAltRounded'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, Link } from 'react-router-dom'

import { registerUser, verifyOtp, logout, clearPendingVerification } from './authSlice'

const schema = yup.object().shape({
  name: yup.string().required('Name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phone: yup.string().required('Phone number is required'),
  password: yup.string().min(6, 'Minimum 6 characters').required('Password is required'),
  confirmPassword: yup
    .string()
    .oneOf([yup.ref('password')], 'Passwords must match')
    .required('Please confirm password'),
})

const otpSchema = yup.object().shape({
  otp: yup
    .string()
    .required('OTP is required')
    .matches(/^[0-9]{4}$/, 'OTP must be 4 digits'),
})

const RegisterPage = () => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const {
    status,
    error,
    token,
    pendingVerificationEmail,
    verificationStatus,
    verificationMessage,
    verificationError,
    registrationMessage,
  } = useSelector((state) => state.auth)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      name: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: '',
    },
  })

  const {
    register: registerOtp,
    handleSubmit: handleOtpSubmit,
    reset: resetOtp,
    formState: { errors: otpErrors },
  } = useForm({
    resolver: yupResolver(otpSchema),
    defaultValues: {
      otp: '',
    },
  })

  useEffect(() => {
    if (token) {
      navigate('/dashboard', { replace: true })
    }
  }, [token, navigate])

  useEffect(() => {
    if (verificationStatus === 'succeeded' && verificationMessage) {
      if (token) {
        dispatch(logout())
      }
      // Store email before redirect (it will be cleared by authSlice after redirect)
      const emailToPass = pendingVerificationEmail
      
      const timer = setTimeout(() => {
        navigate('/login', {
          replace: true,
          state: { 
            message: verificationMessage || 'Verification successful! Please login.',
            email: emailToPass || null, // Pass email for auto-fill
          },
        })
        // Clear pending verification state after redirect
        dispatch(clearPendingVerification())
      }, 1500)
      return () => clearTimeout(timer)
    }
    return undefined
  }, [verificationStatus, verificationMessage, navigate, dispatch, token, pendingVerificationEmail])

  const onSubmit = (values) => {
    try {
      const payload = {
        name: values.name,
        email: values.email,
        phone: values.phone,
        password: values.password,
      }

      console.log('Registering user with payload:', { ...payload, password: '***' })
      dispatch(registerUser(payload))
    } catch (err) {
      console.error('Registration form error:', err)
    }
  }

  const onVerifyOtp = (values) => {
    if (!pendingVerificationEmail) {
      console.error('No pending verification email found')
      return
    }
    console.log('Verifying OTP for email:', pendingVerificationEmail)
    console.log('OTP entered:', values.otp)
    dispatch(
      verifyOtp({
        email: pendingVerificationEmail,
        otp: values.otp,
      }),
    ).then((action) => {
      console.log('OTP verification action result:', action)
      if (action.type.endsWith('fulfilled')) {
        console.log('OTP verification successful')
        resetOtp()
      } else if (action.type.endsWith('rejected')) {
        console.error('OTP verification failed:', action.payload)
      }
    }).catch((error) => {
      console.error('OTP verification error:', error)
    })
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
      <Card sx={{ width: '100%', maxWidth: 900 }}>
        <CardContent sx={{ p: { xs: 3, md: 5 } }}>
          <Stack spacing={1} mb={4} alignItems="center" textAlign="center">
            <Typography variant="h4" fontWeight={700}>
              Create your account
            </Typography>
            <Typography color="text.secondary">
              Join the community to start sharing rides. Add a vehicle later to post your own rides.
            </Typography>
          </Stack>

          {error && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {error}
            </Alert>
          )}
          {registrationMessage && (
            <Alert severity="info" sx={{ mb: 3 }}>
              {registrationMessage}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <TextField
                  label="Full name"
                  fullWidth
                  {...register('name')}
                  error={!!errors.name}
                  helperText={errors.name?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Email address"
                  type="email"
                  fullWidth
                  {...register('email')}
                  error={!!errors.email}
                  helperText={errors.email?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Phone number"
                  fullWidth
                  {...register('phone')}
                  error={!!errors.phone}
                  helperText={errors.phone?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Password"
                  type="password"
                  fullWidth
                  {...register('password')}
                  error={!!errors.password}
                  helperText={errors.password?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Confirm password"
                  type="password"
                  fullWidth
                  {...register('confirmPassword')}
                  error={!!errors.confirmPassword}
                  helperText={errors.confirmPassword?.message}
                />
              </Grid>
            </Grid>

            <Button
              type="submit"
              size="large"
              variant="contained"
              fullWidth
              sx={{ mt: 4 }}
              startIcon={<PersonAddAltRoundedIcon />}
              disabled={status === 'loading' || Boolean(pendingVerificationEmail)}
            >
              {status === 'loading' ? 'Creating account...' : 'Create account'}
            </Button>
          </Box>

          {pendingVerificationEmail && (
            <Box mt={4}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" mb={1}>
                    Verify your email
                  </Typography>
                  <Typography variant="body2" color="text.secondary" mb={2}>
                    We sent a 4-digit code to <strong>{pendingVerificationEmail}</strong>. Enter it
                    below to activate your account.
                  </Typography>
                  {verificationStatus === 'succeeded' && verificationMessage && (
                    <Alert severity="success" sx={{ mb: 2 }}>
                      {verificationMessage}
                    </Alert>
                  )}
                  {verificationError && (
                    <Alert severity="error" sx={{ mb: 2 }}>
                      {verificationError}
                    </Alert>
                  )}
                  <Box component="form" onSubmit={handleOtpSubmit(onVerifyOtp)}>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          label="Enter OTP"
                          fullWidth
                          {...registerOtp('otp')}
                          error={!!otpErrors.otp}
                          helperText={otpErrors.otp?.message || 'Check your email for the code'}
                          inputProps={{ maxLength: 4 }}
                        />
                      </Grid>
                      <Grid item xs={12} md={6} display="flex" alignItems="center">
                        <Button
                          type="submit"
                          variant="contained"
                          fullWidth
                          disabled={verificationStatus === 'loading'}
                        >
                          {verificationStatus === 'loading' ? 'Verifying...' : 'Verify OTP'}
                        </Button>
                      </Grid>
                    </Grid>
                  </Box>
                </CardContent>
              </Card>
            </Box>
          )}

          <Typography textAlign="center" mt={3} variant="body2">
            Already have an account?{' '}
            <Link to="/login" style={{ color: '#0F8B8D', fontWeight: 600 }}>
              Sign in
            </Link>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  )
}

export default RegisterPage

