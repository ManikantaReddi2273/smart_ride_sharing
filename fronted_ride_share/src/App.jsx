import { Navigate, Route, Routes } from 'react-router-dom'

import AppLayout from './components/layout/AppLayout'
import ProtectedRoute from './components/routing/ProtectedRoute'
import PublicRoute from './components/routing/PublicRoute'
import NotImplementedPage from './components/common/NotImplementedPage'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import DashboardPage from './features/dashboard/DashboardPage'
import RidePostPage from './features/rides/RidePostPage'
import RideSearchPage from './features/rides/RideSearchPage'
import MyRidesPage from './features/rides/MyRidesPage'
import BookingsPage from './features/rides/BookingsPage'
import ProfilePage from './features/profile/ProfilePage'

const App = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/dashboard" replace />} />

    <Route
      path="/login"
      element={
        <PublicRoute>
          <LoginPage />
        </PublicRoute>
      }
    />

    <Route
      path="/register"
      element={
        <PublicRoute>
          <RegisterPage />
        </PublicRoute>
      }
    />

    <Route
      element={
        <ProtectedRoute>
          <AppLayout />
        </ProtectedRoute>
      }
    >
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/rides/post" element={<RidePostPage />} />
      <Route path="/rides/search" element={<RideSearchPage />} />
      <Route path="/rides/my-rides" element={<MyRidesPage />} />
      <Route path="/bookings" element={<BookingsPage />} />
      <Route path="/payments" element={<NotImplementedPage title="Payments - Not Yet Implemented" />} />
      <Route path="/reviews" element={<NotImplementedPage title="Reviews - Not Yet Implemented" />} />
      <Route path="/settings" element={<NotImplementedPage title="Settings - Not Yet Implemented" />} />
      <Route path="/profile" element={<ProfilePage />} />
    </Route>

    <Route path="*" element={<Navigate to="/dashboard" replace />} />
  </Routes>
  )

export default App
