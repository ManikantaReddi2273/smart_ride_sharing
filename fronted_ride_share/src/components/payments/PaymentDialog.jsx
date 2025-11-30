import React, { useState, useEffect } from 'react'
import PropTypes from 'prop-types'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Alert,
  CircularProgress,
  Stack,
} from '@mui/material'
import { useDispatch } from 'react-redux'
import { verifyPayment } from '../../features/rides/rideSlice'

/**
 * Payment Dialog Component
 * Handles Razorpay payment checkout and verification
 */
const PaymentDialog = ({ open, onClose, booking, paymentOrder, onPaymentSuccess }) => {
  const dispatch = useDispatch()
  const [paymentStatus, setPaymentStatus] = useState('idle') // idle, processing, success, failed
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      console.log('💳 PaymentDialog opened:', { 
        hasBooking: !!booking, 
        hasPaymentOrder: !!paymentOrder,
        paymentOrder: paymentOrder 
      })
      setPaymentStatus('idle')
      setError(null)
    }
  }, [open, paymentOrder, booking])

  const handlePayment = () => {
    if (!paymentOrder || !booking) {
      setError('Payment information is missing')
      return
    }

    if (!paymentOrder.keyId) {
      setError('Payment gateway key is missing. Please refresh and try again.')
      return
    }

    setPaymentStatus('processing')
    setError(null)

    // Initialize Razorpay checkout
    const options = {
      key: paymentOrder.keyId,
      amount: paymentOrder.amount,
      currency: paymentOrder.currency || 'INR',
      name: 'Smart Ride Sharing',
      description: `Payment for ride booking #${booking.id}`,
      order_id: paymentOrder.orderId,
      handler: function (response) {
        console.log('Payment successful:', response)

        // Verify payment with backend
        const paymentVerificationData = {
          paymentId: booking.paymentId,
          razorpayPaymentId: response.razorpay_payment_id,
          razorpayOrderId: response.razorpay_order_id,
          razorpaySignature: response.razorpay_signature,
        }

        dispatch(
          verifyPayment({
            bookingId: booking.id,
            paymentVerificationData: paymentVerificationData,
          })
        )
          .then((action) => {
            if (action.type.endsWith('fulfilled')) {
              setPaymentStatus('success')
              if (onPaymentSuccess) {
                setTimeout(() => {
                  onPaymentSuccess()
                  onClose()
                }, 2000)
              } else {
                setTimeout(() => {
                  onClose()
                }, 2000)
              }
            } else {
              setPaymentStatus('failed')
              setError(action.payload || 'Payment verification failed')
            }
          })
          .catch((err) => {
            setPaymentStatus('failed')
            setError(err.message || 'Payment verification failed')
          })
      },
      prefill: {
        // Pre-fill user details if available
      },
      theme: {
        color: '#1976d2',
      },
      modal: {
        ondismiss: function () {
          console.log('Payment dialog closed by user')
          setPaymentStatus('idle')
          // Don't close the dialog - let user retry
        },
      },
    }

    // Check if Razorpay is loaded
    if (window.Razorpay) {
      try {
        const razorpay = new window.Razorpay(options)
        razorpay.open()
      } catch (err) {
        setPaymentStatus('failed')
        setError('Failed to open payment gateway: ' + err.message)
      }
    } else {
      setPaymentStatus('failed')
      setError('Payment gateway not available. Please refresh the page and try again.')
    }
  }

  const formatAmount = (amountInPaise) => {
    return `₹${(amountInPaise / 100).toFixed(2)}`
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Typography variant="h6" fontWeight={700}>
          Complete Payment
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && (
            <Alert severity="error" onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {paymentStatus === 'success' && (
            <Alert severity="success">
              Payment successful! Your booking has been confirmed.
            </Alert>
          )}

          {paymentOrder && booking && paymentOrder.orderId && (
            <>
              <Box>
                <Typography variant="body2" color="text.secondary">
                  Booking ID
                </Typography>
                <Typography variant="body1" fontWeight={600}>
                  #{booking.id}
                </Typography>
              </Box>

              <Box>
                <Typography variant="body2" color="text.secondary">
                  Amount to Pay
                </Typography>
                <Typography variant="h5" color="primary" fontWeight={700}>
                  {formatAmount(paymentOrder.amount)}
                </Typography>
              </Box>

              <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  Payment Details
                </Typography>
                <Stack spacing={0.5}>
                  <Typography variant="body2">
                    <strong>Order ID:</strong> {paymentOrder.orderId}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Currency:</strong> {paymentOrder.currency || 'INR'}
                  </Typography>
                </Stack>
              </Box>

              {paymentStatus === 'processing' && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <CircularProgress size={20} />
                  <Typography variant="body2">Processing payment...</Typography>
                </Box>
              )}
            </>
          )}

          {!paymentOrder && (
            <Alert severity="warning">
              Payment information is not available. Please try again from My Bookings page.
            </Alert>
          )}
          
          {paymentOrder && !paymentOrder.orderId && (
            <Alert severity="error">
              Payment order is incomplete. Missing order ID. Please contact support.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={paymentStatus === 'processing'}>
          Cancel
        </Button>
        <Button
          onClick={handlePayment}
          variant="contained"
          disabled={paymentStatus === 'processing' || paymentStatus === 'success' || !paymentOrder}
          size="large"
        >
          {paymentStatus === 'processing' ? (
            <>
              <CircularProgress size={16} sx={{ mr: 1 }} />
              Processing...
            </>
          ) : paymentStatus === 'success' ? (
            'Payment Successful'
          ) : (
            'Pay Now'
          )}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

PaymentDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  booking: PropTypes.object,
  paymentOrder: PropTypes.object,
  onPaymentSuccess: PropTypes.func,
}

export default PaymentDialog
