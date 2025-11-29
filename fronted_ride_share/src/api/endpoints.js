export const endpoints = {
  auth: {
    login: '/users/login',
    register: '/users/register',
    profile: '/users/profile',
    refresh: '/users/refresh',
    verifyOtp: '/users/verify-otp',
  },
  vehicles: {
    list: '/users/vehicles',
    create: '/users/vehicles',
  },
  rides: {
    base: '/rides',
    search: '/rides/search',
    myRides: '/rides/my-rides',
    myBookings: '/rides/my-bookings',
    calculateFare: '/rides/calculate-fare',
    addressSuggestions: '/rides/address-suggestions',
    status: (rideId) => `/rides/${rideId}/status`,
    detail: (rideId) => `/rides/${rideId}`,
    book: (rideId) => `/rides/${rideId}/book`,
  },
}

export default endpoints

