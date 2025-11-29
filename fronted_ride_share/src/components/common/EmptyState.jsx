import PropTypes from 'prop-types'
import { Box, Typography } from '@mui/material'

const EmptyState = ({ title, description, icon }) => (
  <Box
    sx={{
      textAlign: 'center',
      py: 6,
      px: 3,
      color: 'text.secondary',
    }}
  >
    <Box sx={{ fontSize: 48, mb: 2 }}>{icon}</Box>
    <Typography variant="h6" fontWeight={600} gutterBottom>
      {title}
    </Typography>
    <Typography variant="body2">{description}</Typography>
  </Box>
)

EmptyState.propTypes = {
  title: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  icon: PropTypes.node,
}

EmptyState.defaultProps = {
  icon: '🛣️',
}

export default EmptyState

