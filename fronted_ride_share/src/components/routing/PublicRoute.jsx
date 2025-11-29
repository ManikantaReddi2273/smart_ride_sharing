import PropTypes from 'prop-types'
import { Navigate } from 'react-router-dom'
import { useSelector } from 'react-redux'

const PublicRoute = ({ children }) => {
  const { token, initializing } = useSelector((state) => state.auth)

  if (initializing) {
    return null
  }

  if (token) {
    return <Navigate to="/dashboard" replace />
  }

  return children
}

PublicRoute.propTypes = {
  children: PropTypes.node.isRequired,
}

export default PublicRoute

