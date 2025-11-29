import { useState } from 'react'
import PropTypes from 'prop-types'
import {
  AppBar,
  Avatar,
  Box,
  Button,
  IconButton,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import NotificationsNoneRoundedIcon from '@mui/icons-material/NotificationsNoneRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import AddRoadRoundedIcon from '@mui/icons-material/AddRoadRounded'
import DirectionsCarFilledRoundedIcon from '@mui/icons-material/DirectionsCarFilledRounded'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'

import { logout } from '../../features/auth/authSlice'
import SearchDialog from '../rides/SearchDialog'

const TopBar = ({ drawerWidth }) => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const { user } = useSelector((state) => state.auth)
  const [searchDialogOpen, setSearchDialogOpen] = useState(false)

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: `calc(100% - ${drawerWidth}px)`,
        ml: `${drawerWidth}px`,
        backgroundColor: '#ffffff',
        color: 'text.primary',
        borderBottom: '1px solid rgba(15, 23, 42, 0.08)',
      }}
    >
      <Toolbar sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
        <Box display="flex" alignItems="center" gap={2}>
          <DirectionsCarFilledRoundedIcon color="primary" />
        </Box>

        <Stack direction="row" alignItems="center" spacing={2}>
          <Button
            color="primary"
            variant="outlined"
            startIcon={<SearchRoundedIcon />}
            onClick={() => setSearchDialogOpen(true)}
            sx={{ display: { xs: 'none', sm: 'flex' } }}
          >
            Search
          </Button>
          <Button
            color="primary"
            variant="contained"
            startIcon={<AddRoadRoundedIcon />}
            onClick={() => navigate('/rides/post')}
            sx={{ display: { xs: 'none', sm: 'flex' } }}
          >
            Post Ride
          </Button>
          <Tooltip title="Notifications">
            <IconButton color="inherit">
              <NotificationsNoneRoundedIcon />
            </IconButton>
          </Tooltip>
          <Box display="flex" alignItems="center" gap={1}>
            <Avatar sx={{ bgcolor: 'primary.main' }}>
              {user?.name?.[0]?.toUpperCase() || user?.email?.[0]?.toUpperCase() || 'U'}
            </Avatar>
            <Typography variant="subtitle2" fontWeight={600}>
              {user?.name || user?.email || 'User'}
            </Typography>
          </Box>
          <Tooltip title="Sign out">
            <IconButton color="inherit" onClick={() => dispatch(logout())}>
              <LogoutRoundedIcon />
            </IconButton>
          </Tooltip>
        </Stack>
      </Toolbar>
      <SearchDialog 
        open={searchDialogOpen} 
        onClose={() => setSearchDialogOpen(false)} 
      />
    </AppBar>
  )
}

TopBar.propTypes = {
  drawerWidth: PropTypes.number.isRequired,
}

export default TopBar

