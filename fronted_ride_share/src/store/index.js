import { configureStore } from '@reduxjs/toolkit'

import authReducer from '../features/auth/authSlice'
import rideReducer from '../features/rides/rideSlice'
import paymentReducer from '../features/payments/paymentSlice'
import { setupInterceptors } from '../api/apiClient'

const store = configureStore({
  reducer: {
    auth: authReducer,
    rides: rideReducer,
    payments: paymentReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      thunk: {
        extraArgument: {},
      },
      serializableCheck: false,
    }),
})

setupInterceptors(store)

export default store

