import PropTypes from 'prop-types'
import { Navigate, useLocation } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { Box, CircularProgress } from '@mui/material'

const ProtectedRoute = ({ children }) => {
  const { token, initializing } = useSelector((state) => state.auth)
  const location = useLocation()

  if (initializing) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#E8F8F5',
        }}
      >
        <CircularProgress color="primary" />
      </Box>
    )
  }

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return children
}

ProtectedRoute.propTypes = {
  children: PropTypes.node.isRequired,
}

export default ProtectedRoute

