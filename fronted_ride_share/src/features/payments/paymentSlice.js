import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { apiClient } from '../../api/apiClient'
import endpoints from '../../api/endpoints'

const handleError = (error) =>
  error?.response?.data?.message || error?.message || 'Unexpected error'

/**
 * Get wallet balance for a user
 */
export const getWalletBalance = createAsyncThunk(
  'payments/getWalletBalance',
  async (userId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.wallet(userId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get wallet transactions for a user
 */
export const getWalletTransactions = createAsyncThunk(
  'payments/getWalletTransactions',
  async (userId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.walletTransactions(userId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get payment by booking ID
 */
export const getPaymentByBookingId = createAsyncThunk(
  'payments/getPaymentByBookingId',
  async (bookingId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.booking(bookingId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get payment order for retry
 */
export const getPaymentOrderForRetry = createAsyncThunk(
  'payments/getPaymentOrderForRetry',
  async (paymentId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.paymentOrder(paymentId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get passenger transactions
 */
export const getPassengerTransactions = createAsyncThunk(
  'payments/getPassengerTransactions',
  async (passengerId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(
        endpoints.payments.transactions + `/passenger/${passengerId}`
      )
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get driver transactions
 */
export const getDriverTransactions = createAsyncThunk(
  'payments/getDriverTransactions',
  async (driverId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(
        endpoints.payments.transactions + `/driver/${driverId}`
      )
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get bank accounts
 */
export const getBankAccounts = createAsyncThunk(
  'payments/getBankAccounts',
  async (_, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.bankAccounts)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Add bank account
 */
export const addBankAccount = createAsyncThunk(
  'payments/addBankAccount',
  async (bankAccountData, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.payments.bankAccounts, bankAccountData)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Delete bank account
 */
export const deleteBankAccount = createAsyncThunk(
  'payments/deleteBankAccount',
  async (accountId, { rejectWithValue }) => {
    try {
      await apiClient.delete(endpoints.payments.bankAccount(accountId))
      return accountId
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Set default bank account
 */
export const setDefaultBankAccount = createAsyncThunk(
  'payments/setDefaultBankAccount',
  async (accountId, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.put(endpoints.payments.setDefaultBankAccount(accountId))
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Get withdrawals
 */
export const getWithdrawals = createAsyncThunk(
  'payments/getWithdrawals',
  async (_, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.get(endpoints.payments.withdrawals)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

/**
 * Request withdrawal
 */
export const requestWithdrawal = createAsyncThunk(
  'payments/requestWithdrawal',
  async (withdrawalData, { rejectWithValue }) => {
    try {
      const { data } = await apiClient.post(endpoints.payments.withdrawals, withdrawalData)
      return data
    } catch (error) {
      return rejectWithValue(handleError(error))
    }
  }
)

const initialState = {
  walletBalance: null,
  walletTransactions: [],
  paymentDetails: null,
  passengerTransactions: [],
  driverTransactions: [],
  bankAccounts: [],
  withdrawals: [],
  status: 'idle',
  error: null,
}

const paymentSlice = createSlice({
  name: 'payments',
  initialState,
  reducers: {
    clearPaymentErrors: (state) => {
      state.error = null
    },
    resetPaymentState: (state) => {
      state.walletBalance = null
      state.walletTransactions = []
      state.paymentDetails = null
      state.passengerTransactions = []
      state.driverTransactions = []
      state.bankAccounts = []
      state.withdrawals = []
      state.error = null
    },
  },
  extraReducers: (builder) => {
    builder
      // Wallet Balance
      .addCase(getWalletBalance.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getWalletBalance.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.walletBalance = action.payload
      })
      .addCase(getWalletBalance.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Wallet Transactions
      .addCase(getWalletTransactions.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getWalletTransactions.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.walletTransactions = action.payload
      })
      .addCase(getWalletTransactions.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Payment by Booking ID
      .addCase(getPaymentByBookingId.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getPaymentByBookingId.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.paymentDetails = action.payload
      })
      .addCase(getPaymentByBookingId.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Payment Order for Retry
      .addCase(getPaymentOrderForRetry.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getPaymentOrderForRetry.fulfilled, (state, action) => {
        state.status = 'succeeded'
        // Store payment order in paymentDetails for use in dialog
        state.paymentDetails = { ...state.paymentDetails, paymentOrder: action.payload }
      })
      .addCase(getPaymentOrderForRetry.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Passenger Transactions
      .addCase(getPassengerTransactions.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getPassengerTransactions.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.passengerTransactions = action.payload
      })
      .addCase(getPassengerTransactions.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Driver Transactions
      .addCase(getDriverTransactions.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getDriverTransactions.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.driverTransactions = action.payload
      })
      .addCase(getDriverTransactions.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      // Bank Accounts
      .addCase(getBankAccounts.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getBankAccounts.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.bankAccounts = action.payload
      })
      .addCase(getBankAccounts.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(addBankAccount.fulfilled, (state, action) => {
        state.bankAccounts.push(action.payload)
      })
      .addCase(deleteBankAccount.fulfilled, (state, action) => {
        state.bankAccounts = state.bankAccounts.filter(acc => acc.id !== action.payload)
      })
      .addCase(setDefaultBankAccount.fulfilled, (state, action) => {
        state.bankAccounts = state.bankAccounts.map(acc =>
          acc.id === action.payload.id ? action.payload : { ...acc, isDefault: false }
        )
      })
      // Withdrawals
      .addCase(getWithdrawals.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(getWithdrawals.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.withdrawals = action.payload
      })
      .addCase(getWithdrawals.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload
      })
      .addCase(requestWithdrawal.fulfilled, (state, action) => {
        state.withdrawals.unshift(action.payload)
      })
  },
})

export const { clearPaymentErrors, resetPaymentState } = paymentSlice.actions
export default paymentSlice.reducer
