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
    verifyPayment: (bookingId) => `/rides/bookings/${bookingId}/verify-payment`,
  },
  payments: {
    initiate: '/payments/initiate',
    verify: '/payments/verify',
    transactions: '/payments/transactions',
    booking: (bookingId) => `/payments/booking/${bookingId}`,
    paymentOrder: (paymentId) => `/payments/${paymentId}/order`,
    wallet: (userId) => `/payments/wallet/${userId}`,
    walletTransactions: (userId) => `/payments/wallet/${userId}/transactions`,
  },
}

export default endpoints

