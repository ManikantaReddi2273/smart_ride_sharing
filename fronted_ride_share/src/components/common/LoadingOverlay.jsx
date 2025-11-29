import PropTypes from 'prop-types'
import { Box, CircularProgress } from '@mui/material'

const LoadingOverlay = ({ isLoading }) =>
  isLoading ? (
    <Box
      sx={{
        position: 'absolute',
        inset: 0,
        backgroundColor: 'rgba(255,255,255,0.65)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 3,
        zIndex: 2,
      }}
    >
      <CircularProgress size={32} />
    </Box>
  ) : null

LoadingOverlay.propTypes = {
  isLoading: PropTypes.bool,
}

LoadingOverlay.defaultProps = {
  isLoading: false,
}

export default LoadingOverlay

