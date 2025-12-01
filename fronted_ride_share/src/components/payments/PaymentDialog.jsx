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
      // CRITICAL: Log fare information for debugging
      if (booking) {
        console.log('💰 Booking Fare Debug:', {
          passengerFare: booking.passengerFare,
          seatsBooked: booking.seatsBooked,
          farePerSeat: booking.passengerFare && booking.seatsBooked 
            ? (booking.passengerFare / booking.seatsBooked).toFixed(2) 
            : 'N/A',
          expectedTotalFare: booking.passengerFare && booking.seatsBooked && booking.passengerFare < 500
            ? `⚠️ WARNING: Fare ${booking.passengerFare} seems too low for ${booking.seatsBooked} seats!`
            : 'OK'
        })
      }
      if (paymentOrder) {
        console.log('💰 Payment Order Debug:', {
          amountPaise: paymentOrder.amount,
          amountRupees: paymentOrder.amount ? (paymentOrder.amount / 100).toFixed(2) : 'N/A',
          currency: paymentOrder.currency
        })
      }
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
                  {/* CRITICAL: Use paymentOrder.amount (in paise) which includes platform fee
                      This is the correct total amount calculated by backend (fare * seats + platform fee) */}
                  {formatAmount(paymentOrder.amount)}
                </Typography>
                {/* Show fare breakdown if booking has passengerFare */}
                {booking?.passengerFare && (
                  <Stack spacing={0.5} sx={{ mt: 1 }}>
                    <Stack direction="row" justifyContent="space-between">
                      <Typography variant="caption" color="text.secondary">
                        Fare ({booking.seatsBooked || 1} seat{booking.seatsBooked > 1 ? 's' : ''}):
                      </Typography>
                      <Typography variant="caption" fontWeight={600}>
                        {/* CRITICAL: Use booking.passengerFare which should already be multiplied by seats
                            If it seems like per-seat fare (fare/seats is reasonable per-seat amount), multiply */}
                        {(() => {
                          const seats = booking.seatsBooked || 1
                          const fare = booking.passengerFare
                          
                          // CRITICAL: Smart detection - if fare divided by seats gives a reasonable per-seat amount
                          // (between 50-500), then the fare is likely per-seat, not total
                          // Example: ₹256.10 / 2 = ₹128.05 per seat (reasonable) → fare is per-seat
                          // Example: ₹512.20 / 2 = ₹256.10 per seat (reasonable) → fare is total
                          // We need to check: if (fare / seats) is in reasonable per-seat range AND
                          // the paymentOrder.amount suggests a higher total, then fare is per-seat
                          
                          const farePerSeat = seats > 1 ? fare / seats : fare
                          const totalAmountFromPayment = paymentOrder.amount / 100
                          
                          // If fare per seat is reasonable (50-500) AND total payment amount is significantly higher
                          // than the fare, then the fare is likely per-seat
                          const isReasonablePerSeat = farePerSeat >= 50 && farePerSeat <= 500
                          const paymentAmountMuchHigher = totalAmountFromPayment > fare * 1.5
                          const isLikelyPerSeat = seats > 1 && isReasonablePerSeat && paymentAmountMuchHigher
                          
                          // Alternative check: if fare * seats * 1.1 (with platform fee) is close to payment amount,
                          // then fare is per-seat
                          const expectedTotalIfPerSeat = (fare * seats) * 1.1
                          const isCloseToExpected = Math.abs(totalAmountFromPayment - expectedTotalIfPerSeat) < 10
                          const isLikelyPerSeatAlt = seats > 1 && isCloseToExpected && farePerSeat < 300
                          
                          const shouldMultiply = isLikelyPerSeat || isLikelyPerSeatAlt
                          const displayFare = shouldMultiply ? fare * seats : fare
                          
                          if (shouldMultiply) {
                            console.warn('⚠️ Detected per-seat fare instead of total. Correcting:', {
                              originalFare: fare,
                              seats: seats,
                              farePerSeat: farePerSeat.toFixed(2),
                              correctedFare: displayFare,
                              paymentTotal: totalAmountFromPayment,
                              expectedIfPerSeat: expectedTotalIfPerSeat.toFixed(2)
                            })
                          }
                          
                          return `₹${displayFare.toFixed(2)}`
                        })()}
                      </Typography>
                    </Stack>
                    <Stack direction="row" justifyContent="space-between">
                      <Typography variant="caption" color="text.secondary">
                        Platform fee (10%):
                      </Typography>
                      <Typography variant="caption" fontWeight={600}>
                        {(() => {
                          const seats = booking.seatsBooked || 1
                          const fare = booking.passengerFare
                          const farePerSeat = seats > 1 ? fare / seats : fare
                          const totalAmountFromPayment = paymentOrder.amount / 100
                          
                          // Use same logic as fare detection
                          const isReasonablePerSeat = farePerSeat >= 50 && farePerSeat <= 500
                          const paymentAmountMuchHigher = totalAmountFromPayment > fare * 1.5
                          const isLikelyPerSeat = seats > 1 && isReasonablePerSeat && paymentAmountMuchHigher
                          
                          const expectedTotalIfPerSeat = (fare * seats) * 1.1
                          const isCloseToExpected = Math.abs(totalAmountFromPayment - expectedTotalIfPerSeat) < 10
                          const isLikelyPerSeatAlt = seats > 1 && isCloseToExpected && farePerSeat < 300
                          
                          const shouldMultiply = isLikelyPerSeat || isLikelyPerSeatAlt
                          const correctedFare = shouldMultiply ? fare * seats : fare
                          const platformFee = totalAmountFromPayment - correctedFare
                          
                          return `₹${platformFee.toFixed(2)}`
                        })()}
                      </Typography>
                    </Stack>
                  </Stack>
                )}
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
