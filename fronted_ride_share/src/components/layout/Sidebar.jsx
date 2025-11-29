import PropTypes from 'prop-types'
import {
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  Divider,
} from '@mui/material'
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded'
import DirectionsCarRoundedIcon from '@mui/icons-material/DirectionsCarRounded'
import BookOnlineRoundedIcon from '@mui/icons-material/BookOnlineRounded'
import PaymentRoundedIcon from '@mui/icons-material/PaymentRounded'
import StarRoundedIcon from '@mui/icons-material/StarRounded'
import PersonRoundedIcon from '@mui/icons-material/PersonRounded'
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import DirectionsCarFilledRoundedIcon from '@mui/icons-material/DirectionsCarFilledRounded'
import { useLocation, useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { logout } from '../../features/auth/authSlice'

const navItems = [
  {
    label: 'Dashboard',
    icon: <DashboardRoundedIcon />,
    path: '/dashboard',
  },
  {
    label: 'My Rides',
    icon: <DirectionsCarRoundedIcon />,
    path: '/rides/my-rides',
  },
  {
    label: 'Bookings',
    icon: <BookOnlineRoundedIcon />,
    path: '/bookings',
  },
  {
    label: 'Payments',
    icon: <PaymentRoundedIcon />,
    path: '/payments',
  },
  {
    label: 'Reviews',
    icon: <StarRoundedIcon />,
    path: '/reviews',
  },
  {
    label: 'Profile',
    icon: <PersonRoundedIcon />,
    path: '/profile',
  },
  {
    label: 'Settings',
    icon: <SettingsRoundedIcon />,
    path: '/settings',
  },
]

const Sidebar = ({ width }) => {
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const location = useLocation()

  // All users can see all menu items (no role filtering)
  const filteredItems = navItems

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  return (
    <Drawer
      variant="permanent"
      sx={{
        width,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width,
          boxSizing: 'border-box',
          background: '#ffffff',
          border: 'none',
          paddingBlock: 3,
        },
      }}
    >
      <Box sx={{ px: 3, pb: 2 }}>
        <Box display="flex" alignItems="center" gap={1}>
          <DirectionsCarFilledRoundedIcon color="primary" />
          <Box>
            <Typography variant="subtitle2" color="text.secondary">
              Smart Ride
            </Typography>
            <Typography variant="h6" fontWeight={700}>
              Sharing
            </Typography>
          </Box>
        </Box>
      </Box>
      <Divider />
      <List sx={{ px: 1 }}>
        {filteredItems.map((item) => {
          const isActive = location.pathname.startsWith(item.path)

          return (
            <ListItemButton
              key={item.label}
              selected={isActive}
              onClick={() => navigate(item.path)}
              sx={{
                borderRadius: 2,
                mb: 1,
                '&.Mui-selected': {
                  backgroundColor: 'rgba(15, 139, 141, 0.12)',
                  color: 'primary.main',
                  '& .MuiListItemIcon-root': {
                    color: 'primary.main',
                  },
                },
              }}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText
                primaryTypographyProps={{ fontWeight: isActive ? 700 : 500 }}
                primary={item.label}
              />
            </ListItemButton>
          )
        })}
      </List>
      <Divider sx={{ my: 2 }} />
      <List sx={{ px: 1 }}>
        <ListItemButton
          onClick={handleLogout}
          sx={{
            borderRadius: 2,
            color: 'error.main',
            '&:hover': {
              backgroundColor: 'rgba(211, 47, 47, 0.08)',
            },
          }}
        >
          <ListItemIcon sx={{ color: 'error.main' }}>
            <LogoutRoundedIcon />
          </ListItemIcon>
          <ListItemText primary="Logout" primaryTypographyProps={{ fontWeight: 500 }} />
        </ListItemButton>
      </List>
    </Drawer>
  )
}

Sidebar.propTypes = {
  width: PropTypes.number.isRequired,
}

export default Sidebar

