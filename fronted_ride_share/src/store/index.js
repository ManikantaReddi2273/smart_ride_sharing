import { configureStore } from '@reduxjs/toolkit'

import authReducer from '../features/auth/authSlice'
import rideReducer from '../features/rides/rideSlice'
import { setupInterceptors } from '../api/apiClient'

const store = configureStore({
  reducer: {
    auth: authReducer,
    rides: rideReducer,
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

