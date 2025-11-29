import PropTypes from 'prop-types'
import { Box, Stack, Typography } from '@mui/material'

const StatCard = ({ label, value, chip, icon }) => (
  <Box
    sx={{
      flex: 1,
      backgroundColor: '#ffffff',
      borderRadius: 3,
      p: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 2,
    }}
  >
    {icon && (
      <Box
        sx={{
          width: 56,
          height: 56,
          borderRadius: 2,
          display: 'grid',
          placeItems: 'center',
          bgcolor: 'rgba(15, 139, 141, 0.1)',
          color: 'primary.main',
        }}
      >
        {icon}
      </Box>
    )}
    <Stack spacing={0.5}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h4" fontWeight={700}>
        {value}
      </Typography>
      {chip}
    </Stack>
  </Box>
)

StatCard.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  chip: PropTypes.node,
  icon: PropTypes.node,
}

StatCard.defaultProps = {
  chip: null,
  icon: null,
}

export default StatCard

