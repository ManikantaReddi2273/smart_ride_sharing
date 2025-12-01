import { configureStore } from '@reduxjs/toolkit'

import authReducer from '../features/auth/authSlice'
import rideReducer from '../features/rides/rideSlice'
import paymentReducer from '../features/payments/paymentSlice'
import notificationReducer from '../features/notifications/notificationSlice'
import reviewReducer from '../features/reviews/reviewSlice'
import { setupInterceptors } from '../api/apiClient'

const store = configureStore({
  reducer: {
    auth: authReducer,
    rides: rideReducer,
    payments: paymentReducer,
    notifications: notificationReducer,
    reviews: reviewReducer,
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

