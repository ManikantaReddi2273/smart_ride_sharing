import React from 'react'
import PropTypes from 'prop-types'
import { Chip } from '@mui/material'
import { CheckCircle, Pending, Error, Cancel } from '@mui/icons-material'

/**
 * Payment Status Badge Component
 * Displays payment status with appropriate color and icon
 */
const PaymentStatusBadge = ({ status }) => {
  const getStatusConfig = (status) => {
    switch (status?.toUpperCase()) {
      case 'SUCCESS':
        return {
          label: 'Paid',
          color: 'success',
          icon: <CheckCircle fontSize="small" />,
        }
      case 'PENDING':
        return {
          label: 'Pending',
          color: 'warning',
          icon: <Pending fontSize="small" />,
        }
      case 'FAILED':
        return {
          label: 'Failed',
          color: 'error',
          icon: <Error fontSize="small" />,
        }
      case 'CANCELLED':
        return {
          label: 'Cancelled',
          color: 'default',
          icon: <Cancel fontSize="small" />,
        }
      case 'REFUNDED':
        return {
          label: 'Refunded',
          color: 'info',
          icon: <CheckCircle fontSize="small" />,
        }
      default:
        return {
          label: 'Unknown',
          color: 'default',
          icon: null,
        }
    }
  }

  const config = getStatusConfig(status)

  return (
    <Chip
      label={config.label}
      color={config.color}
      icon={config.icon}
      size="small"
      variant="outlined"
    />
  )
}

PaymentStatusBadge.propTypes = {
  status: PropTypes.string,
}

export default PaymentStatusBadge
