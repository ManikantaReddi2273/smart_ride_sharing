import PropTypes from 'prop-types'
import { Box } from '@mui/material'

const PageContainer = ({ children }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 3,
    }}
  >
    {children}
  </Box>
)

PageContainer.propTypes = {
  children: PropTypes.node.isRequired,
}

export default PageContainer

