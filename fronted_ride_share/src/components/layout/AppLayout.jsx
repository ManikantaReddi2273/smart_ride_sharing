import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { Box, Toolbar } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'

import Sidebar from './Sidebar'
import TopBar from './TopBar'
import { fetchProfile } from '../../features/auth/authSlice'

const drawerWidth = 260

const AppLayout = () => {
  const dispatch = useDispatch()
  const { token, user } = useSelector((state) => state.auth)

  useEffect(() => {
    if (token && !user) {
      dispatch(fetchProfile())
    }
  }, [dispatch, token, user])

  return (
    <Box sx={{ display: 'flex', backgroundColor: '#E8F8F5', minHeight: '100vh' }}>
      <Sidebar width={drawerWidth} />
      <Box component="main" sx={{ flexGrow: 1 }}>
        <TopBar drawerWidth={drawerWidth} />
        <Toolbar />
        <Box
          sx={{
            p: { xs: 2, md: 4 },
            maxWidth: 1400,
            marginInline: 'auto',
            width: '100%',
          }}
        >
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}

export default AppLayout

