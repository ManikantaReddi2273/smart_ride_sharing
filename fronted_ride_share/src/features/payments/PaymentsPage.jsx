import { useEffect, useState, useMemo } from 'react'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Divider,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
} from '@mui/material'
import {
  AccountBalanceRounded,
  AddRounded,
  DeleteRounded,
  DownloadRounded,
  FilterListRounded,
  RefreshRounded,
  TrendingUpRounded,
  TrendingDownRounded,
  AccountBalanceWalletRounded,
  CreditCardRounded,
  CheckCircleRounded,
  CancelRounded,
  ScheduleRounded,
} from '@mui/icons-material'
import { useDispatch, useSelector } from 'react-redux'

import PageContainer from '../../components/common/PageContainer'
import EmptyState from '../../components/common/EmptyState'
import {
  getPassengerTransactions,
  getDriverTransactions,
  getWalletBalance,
  getWalletTransactions,
  getBankAccounts,
  addBankAccount,
  deleteBankAccount,
  setDefaultBankAccount,
  getWithdrawals,
  requestWithdrawal,
  clearPaymentErrors,
} from './paymentSlice'

const formatCurrency = (amount) => {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(amount || 0)
}

const formatDate = (dateString) => {
  if (!dateString) return '—'
  try {
    return new Date(dateString).toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch (e) {
    return dateString
  }
}

const PaymentsPage = () => {
  const dispatch = useDispatch()
  const { user } = useSelector((state) => state.auth)
  const {
    passengerTransactions,
    driverTransactions,
    walletBalance,
    walletTransactions,
    bankAccounts,
    withdrawals,
    status,
    error,
  } = useSelector((state) => state.payments)

  const [activeTab, setActiveTab] = useState(0)
  const [bankAccountDialogOpen, setBankAccountDialogOpen] = useState(false)
  const [withdrawalDialogOpen, setWithdrawalDialogOpen] = useState(false)
  const [filterStatus, setFilterStatus] = useState('ALL')
  const [filterDateFrom, setFilterDateFrom] = useState(null)
  const [filterDateTo, setFilterDateTo] = useState(null)
  const [filterMinAmount, setFilterMinAmount] = useState('')
  const [filterMaxAmount, setFilterMaxAmount] = useState('')

  const userId = user?.id
  const userRole = user?.role
  const isDriver = userRole === 'DRIVER' || userRole === 'driver'

  // Show loading state if user info not available yet
  if (!userId) {
    return (
      <PageContainer>
        <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
          <Typography>Loading user information...</Typography>
        </Box>
      </PageContainer>
    )
  }

  useEffect(() => {
    if (userId) {
      dispatch(getWalletBalance(userId))
      dispatch(getWalletTransactions(userId))
      dispatch(getBankAccounts())
      dispatch(getWithdrawals())

      if (isDriver) {
        dispatch(getDriverTransactions(userId))
      } else {
        dispatch(getPassengerTransactions(userId))
      }
    }
  }, [dispatch, userId, isDriver])

  // Filter transactions
  const filteredTransactions = useMemo(() => {
    const transactions = isDriver ? driverTransactions : passengerTransactions

    return transactions.filter((txn) => {
      // Status filter
      if (filterStatus !== 'ALL' && txn.status !== filterStatus) {
        return false
      }

      // Date filter
      if (filterDateFrom || filterDateTo) {
        const txnDate = new Date(txn.createdAt || txn.created_at)
        if (filterDateFrom && txnDate < filterDateFrom) return false
        if (filterDateTo) {
          const toDate = new Date(filterDateTo)
          toDate.setHours(23, 59, 59, 999)
          if (txnDate > toDate) return false
        }
      }

      // Amount filter
      const txnAmount = txn.amount || txn.fare || 0
      if (filterMinAmount && txnAmount < parseFloat(filterMinAmount)) {
        return false
      }
      if (filterMaxAmount && txnAmount > parseFloat(filterMaxAmount)) {
        return false
      }

      return true
    })
  }, [
    isDriver,
    driverTransactions,
    passengerTransactions,
    filterStatus,
    filterDateFrom,
    filterDateTo,
    filterMinAmount,
    filterMaxAmount,
  ])

  const handleRefresh = () => {
    if (userId) {
      dispatch(getWalletBalance(userId))
      dispatch(getWalletTransactions(userId))
      dispatch(getBankAccounts())
      dispatch(getWithdrawals())
      if (isDriver) {
        dispatch(getDriverTransactions(userId))
      } else {
        dispatch(getPassengerTransactions(userId))
      }
    }
  }

  const handleDownloadReceipt = (transaction) => {
    try {
      // Use jsPDF from CDN (loaded in index.html via script tag)
      // This avoids Vite import analysis issues - no npm package needed
      // CDN script exposes jsPDF as window.jspdf.jsPDF
      let jsPDF
      
      // Check different possible CDN export formats
      if (window.jspdf && window.jspdf.jsPDF) {
        jsPDF = window.jspdf.jsPDF
      } else if (window.jsPDF) {
        jsPDF = window.jsPDF
      } else if (window.jspdf) {
        jsPDF = window.jspdf
      } else {
        throw new Error(
          'jsPDF library not loaded. Please refresh the page or check your internet connection.'
        )
      }
      
      // Generate PDF receipt using jsPDF
      const doc = new jsPDF()
      const pageWidth = doc.internal.pageSize.getWidth()
      const margin = 20
      let yPos = margin

      // Header
      doc.setFontSize(20)
      doc.setFont(undefined, 'bold')
      doc.text('SMART RIDE SHARING', pageWidth / 2, yPos, { align: 'center' })
      yPos += 8
      doc.setFontSize(14)
      doc.setFont(undefined, 'normal')
      doc.text('PAYMENT RECEIPT', pageWidth / 2, yPos, { align: 'center' })
      yPos += 15

      // Draw line
      doc.setLineWidth(0.5)
      doc.line(margin, yPos, pageWidth - margin, yPos)
      yPos += 10

      // Transaction Details
      doc.setFontSize(12)
      doc.setFont(undefined, 'bold')
      doc.text('Transaction Details', margin, yPos)
      yPos += 8

      doc.setFontSize(10)
      doc.setFont(undefined, 'normal')
      doc.text(`Transaction ID: #${transaction.id}`, margin, yPos)
      yPos += 6
      doc.text(`Date: ${formatDate(transaction.createdAt || transaction.created_at)}`, margin, yPos)
      yPos += 6
      doc.text(
        `${isDriver ? 'Earnings' : 'Payment'} Amount: ${formatCurrency(transaction.amount || transaction.fare)}`,
        margin,
        yPos
      )
      yPos += 6
      doc.text(`Status: ${transaction.status}`, margin, yPos)
      yPos += 6
      doc.text(`Booking ID: #${transaction.bookingId || 'N/A'}`, margin, yPos)
      yPos += 10

      // Payment Information
      doc.setFont(undefined, 'bold')
      doc.text('Payment Information', margin, yPos)
      yPos += 8

      doc.setFont(undefined, 'normal')
      doc.text(`${isDriver ? 'Driver' : 'Passenger'} ID: ${userId}`, margin, yPos)
      yPos += 6
      doc.text('Payment Method: Razorpay', margin, yPos)
      yPos += 6
      if (transaction.razorpayOrderId) {
        doc.text(`Razorpay Order ID: ${transaction.razorpayOrderId}`, margin, yPos)
        yPos += 6
      }
      if (transaction.razorpayPaymentId) {
        doc.text(`Razorpay Payment ID: ${transaction.razorpayPaymentId}`, margin, yPos)
        yPos += 6
      }
      yPos += 10

      // Footer
      doc.setLineWidth(0.5)
      doc.line(margin, yPos, pageWidth - margin, yPos)
      yPos += 10
      doc.setFontSize(10)
      doc.setFont(undefined, 'italic')
      doc.text('Thank you for using Smart Ride Sharing!', pageWidth / 2, yPos, { align: 'center' })
      yPos += 5
      doc.setFont(undefined, 'normal')
      doc.setFontSize(8)
      doc.text('This is a computer-generated receipt.', pageWidth / 2, yPos, { align: 'center' })

      // Save PDF
      const fileName = `receipt_${transaction.id}_${new Date().getTime()}.pdf`
      doc.save(fileName)
    } catch (error) {
      console.error('Failed to generate PDF:', error)
      alert(
        'Failed to generate PDF receipt: ' + error.message + '\n\n' +
        'Please refresh the page to reload the jsPDF library from CDN.'
      )
    }
  }

  return (
    <PageContainer>
      <Box>
        {/* Header */}
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Box>
            <Typography variant="h4" fontWeight={700}>
              Payments & Transactions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              View your payment history, manage bank accounts, and withdraw earnings
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={<RefreshRounded />}
            onClick={handleRefresh}
            disabled={status === 'loading'}
          >
            Refresh
          </Button>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 3 }} onClose={() => dispatch(clearPaymentErrors())}>
            {error}
          </Alert>
        )}

        {/* Wallet Balance Card (for drivers) */}
        {isDriver && walletBalance && (
          <Card sx={{ mb: 3, bgcolor: 'primary.main', color: 'white' }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography variant="body2" sx={{ opacity: 0.9 }}>
                    Wallet Balance
                  </Typography>
                  <Typography variant="h3" fontWeight={700}>
                    {formatCurrency(walletBalance.balance)}
                  </Typography>
                </Box>
                <AccountBalanceWalletRounded sx={{ fontSize: 60, opacity: 0.3 }} />
              </Stack>
            </CardContent>
          </Card>
        )}

        {/* Tabs */}
        <Paper sx={{ mb: 3 }}>
          <Tabs
            value={activeTab}
            onChange={(e, newValue) => setActiveTab(newValue)}
            variant="scrollable"
            scrollButtons="auto"
          >
            <Tab label="Transaction History" />
            {isDriver && <Tab label="Bank Accounts" />}
            {isDriver && <Tab label="Withdrawals" />}
          </Tabs>
        </Paper>

        {/* Tab Content */}
        {activeTab === 0 && (
          <Box>
            {/* Filters */}
            <Paper sx={{ p: 2, mb: 3 }}>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                <FormControl size="small" sx={{ minWidth: 150 }}>
                  <InputLabel>Status</InputLabel>
                  <Select
                    value={filterStatus}
                    label="Status"
                    onChange={(e) => setFilterStatus(e.target.value)}
                  >
                    <MenuItem value="ALL">All Status</MenuItem>
                    <MenuItem value="SUCCESS">Success</MenuItem>
                    <MenuItem value="PENDING">Pending</MenuItem>
                    <MenuItem value="FAILED">Failed</MenuItem>
                  </Select>
                </FormControl>

                <TextField
                  label="From Date"
                  type="date"
                  size="small"
                  value={filterDateFrom ? filterDateFrom.toISOString().split('T')[0] : ''}
                  onChange={(e) => setFilterDateFrom(e.target.value ? new Date(e.target.value) : null)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 150 }}
                />
                <TextField
                  label="To Date"
                  type="date"
                  size="small"
                  value={filterDateTo ? filterDateTo.toISOString().split('T')[0] : ''}
                  onChange={(e) => setFilterDateTo(e.target.value ? new Date(e.target.value) : null)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 150 }}
                />

                <TextField
                  label="Min Amount (₹)"
                  type="number"
                  size="small"
                  value={filterMinAmount}
                  onChange={(e) => setFilterMinAmount(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 150 }}
                  inputProps={{ min: 0, step: 0.01 }}
                />

                <TextField
                  label="Max Amount (₹)"
                  type="number"
                  size="small"
                  value={filterMaxAmount}
                  onChange={(e) => setFilterMaxAmount(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 150 }}
                  inputProps={{ min: 0, step: 0.01 }}
                />

                {(filterStatus !== 'ALL' ||
                  filterDateFrom ||
                  filterDateTo ||
                  filterMinAmount ||
                  filterMaxAmount) && (
                  <Button
                    size="small"
                    onClick={() => {
                      setFilterStatus('ALL')
                      setFilterDateFrom(null)
                      setFilterDateTo(null)
                      setFilterMinAmount('')
                      setFilterMaxAmount('')
                    }}
                  >
                    Clear Filters
                  </Button>
                )}
              </Stack>
            </Paper>

            {/* Transactions Table */}
            {status === 'loading' && <LinearProgress />}

            {filteredTransactions.length === 0 ? (
              <EmptyState
                icon={<CreditCardRounded />}
                title="No transactions found"
                description="You don't have any transactions matching your filters"
              />
            ) : (
              <TableContainer component={Paper}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Transaction ID</TableCell>
                      <TableCell>Description</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="center">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredTransactions.map((txn) => (
                      <TableRow key={txn.id} hover>
                        <TableCell>{formatDate(txn.createdAt || txn.created_at)}</TableCell>
                        <TableCell>
                          <Typography variant="body2" fontFamily="monospace">
                            #{txn.id}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {isDriver
                            ? `Earnings from booking #${txn.bookingId || 'N/A'}`
                            : `Payment for booking #${txn.bookingId || 'N/A'}`}
                        </TableCell>
                        <TableCell align="right">
                          <Typography
                            variant="body2"
                            fontWeight={600}
                            color={isDriver ? 'success.main' : 'text.primary'}
                          >
                            {isDriver ? '+' : '-'}
                            {formatCurrency(txn.amount || txn.fare)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={txn.status}
                            size="small"
                            color={
                              txn.status === 'SUCCESS'
                                ? 'success'
                                : txn.status === 'PENDING'
                                  ? 'warning'
                                  : 'error'
                            }
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Tooltip title="Download Receipt">
                            <IconButton
                              size="small"
                              onClick={() => handleDownloadReceipt(txn)}
                            >
                              <DownloadRounded fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        )}

        {/* Bank Accounts Tab (Drivers only) */}
        {activeTab === 1 && isDriver && (
          <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
              <Typography variant="h6">Bank Accounts</Typography>
              <Button
                variant="contained"
                startIcon={<AddRounded />}
                onClick={() => setBankAccountDialogOpen(true)}
              >
                Add Bank Account
              </Button>
            </Box>

            {bankAccounts.length === 0 ? (
              <EmptyState
                icon={<AccountBalanceRounded />}
                title="No bank accounts"
                description="Add a bank account to withdraw your earnings"
              />
            ) : (
              <Grid container spacing={2}>
                {bankAccounts.map((account) => (
                  <Grid item xs={12} md={6} key={account.id}>
                    <Card>
                      <CardContent>
                        <Stack spacing={2}>
                          <Box display="flex" justifyContent="space-between" alignItems="start">
                            <Box>
                              <Typography variant="h6">{account.bankName}</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {account.accountNumber} • {account.ifscCode}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {account.accountHolderName}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={1}>
                              {account.isDefault && (
                                <Chip label="Default" size="small" color="primary" />
                              )}
                              {account.isVerified ? (
                                <Chip
                                  label="Verified"
                                  size="small"
                                  color="success"
                                  icon={<CheckCircleRounded />}
                                />
                              ) : (
                                <Chip
                                  label="Pending"
                                  size="small"
                                  color="warning"
                                  icon={<ScheduleRounded />}
                                />
                              )}
                            </Stack>
                          </Box>
                          <Divider />
                          <Stack direction="row" spacing={1}>
                            {!account.isDefault && (
                              <Button
                                size="small"
                                onClick={() => dispatch(setDefaultBankAccount(account.id))}
                              >
                                Set as Default
                              </Button>
                            )}
                            <Button
                              size="small"
                              color="error"
                              startIcon={<DeleteRounded />}
                              onClick={() => {
                                if (window.confirm('Are you sure you want to delete this bank account?')) {
                                  dispatch(deleteBankAccount(account.id))
                                }
                              }}
                            >
                              Delete
                            </Button>
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            )}

            {/* Add Bank Account Dialog */}
            <BankAccountDialog
              open={bankAccountDialogOpen}
              onClose={() => setBankAccountDialogOpen(false)}
            />
          </Box>
        )}

        {/* Withdrawals Tab (Drivers only) */}
        {activeTab === 2 && isDriver && (
          <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
              <Typography variant="h6">Withdrawal History</Typography>
              <Button
                variant="contained"
                startIcon={<TrendingDownRounded />}
                onClick={() => setWithdrawalDialogOpen(true)}
                disabled={!walletBalance || walletBalance.balance < 100}
              >
                Request Withdrawal
              </Button>
            </Box>

            {withdrawals.length === 0 ? (
              <EmptyState
                icon={<TrendingDownRounded />}
                title="No withdrawals"
                description="You haven't made any withdrawal requests yet"
              />
            ) : (
              <TableContainer component={Paper}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Amount</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Transaction ID</TableCell>
                      <TableCell>Notes</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {withdrawals.map((withdrawal) => (
                      <TableRow key={withdrawal.id} hover>
                        <TableCell>{formatDate(withdrawal.createdAt)}</TableCell>
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>
                            {formatCurrency(withdrawal.amount)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={withdrawal.status}
                            size="small"
                            color={
                              withdrawal.status === 'SUCCESS'
                                ? 'success'
                                : withdrawal.status === 'PENDING' || withdrawal.status === 'PROCESSING'
                                  ? 'warning'
                                  : 'error'
                            }
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" fontFamily="monospace">
                            {withdrawal.transactionId || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" color="text.secondary">
                            {withdrawal.failureReason || '—'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            {/* Withdrawal Dialog */}
            <WithdrawalDialog
              open={withdrawalDialogOpen}
              onClose={() => {
                setWithdrawalDialogOpen(false)
                // Refresh wallet balance and withdrawals after closing
                if (userId) {
                  dispatch(getWalletBalance(userId))
                  dispatch(getWithdrawals())
                }
              }}
              walletBalance={walletBalance?.balance || 0}
              bankAccounts={bankAccounts.filter((acc) => acc.isVerified)}
              userId={userId}
            />
          </Box>
        )}
      </Box>
    </PageContainer>
  )
}

// Bank Account Dialog Component
const BankAccountDialog = ({ open, onClose }) => {
  const dispatch = useDispatch()
  const { status } = useSelector((state) => state.payments)
  const [formData, setFormData] = useState({
    accountHolderName: '',
    accountNumber: '',
    ifscCode: '',
    bankName: '',
    accountType: 'SAVINGS',
    isDefault: false,
  })
  const [errors, setErrors] = useState({})

  const handleSubmit = () => {
    // Validation
    const newErrors = {}
    if (!formData.accountHolderName.trim()) {
      newErrors.accountHolderName = 'Account holder name is required'
    }
    if (!formData.accountNumber.trim() || !/^\d{9,18}$/.test(formData.accountNumber)) {
      newErrors.accountNumber = 'Valid account number (9-18 digits) is required'
    }
    if (!formData.ifscCode.trim() || !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(formData.ifscCode.toUpperCase())) {
      newErrors.ifscCode = 'Valid IFSC code is required (e.g., HDFC0001234)'
    }
    if (!formData.bankName.trim()) {
      newErrors.bankName = 'Bank name is required'
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      return
    }

    dispatch(addBankAccount({ ...formData, ifscCode: formData.ifscCode.toUpperCase() })).then(
      (action) => {
        if (action.type.endsWith('fulfilled')) {
          onClose()
          setFormData({
            accountHolderName: '',
            accountNumber: '',
            ifscCode: '',
            bankName: '',
            accountType: 'SAVINGS',
            isDefault: false,
          })
          setErrors({})
        }
      }
    )
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add Bank Account</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Account Holder Name"
            fullWidth
            value={formData.accountHolderName}
            onChange={(e) =>
              setFormData({ ...formData, accountHolderName: e.target.value })
            }
            error={!!errors.accountHolderName}
            helperText={errors.accountHolderName}
            required
          />
          <TextField
            label="Account Number"
            fullWidth
            value={formData.accountNumber}
            onChange={(e) =>
              setFormData({ ...formData, accountNumber: e.target.value.replace(/\D/g, '') })
            }
            error={!!errors.accountNumber}
            helperText={errors.accountNumber || '9-18 digits'}
            required
          />
          <TextField
            label="IFSC Code"
            fullWidth
            value={formData.ifscCode}
            onChange={(e) =>
              setFormData({
                ...formData,
                ifscCode: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''),
              })
            }
            error={!!errors.ifscCode}
            helperText={errors.ifscCode || 'e.g., HDFC0001234'}
            required
            inputProps={{ maxLength: 11 }}
          />
          <TextField
            label="Bank Name"
            fullWidth
            value={formData.bankName}
            onChange={(e) => setFormData({ ...formData, bankName: e.target.value })}
            error={!!errors.bankName}
            helperText={errors.bankName}
            required
          />
          <FormControl fullWidth>
            <InputLabel>Account Type</InputLabel>
            <Select
              value={formData.accountType}
              label="Account Type"
              onChange={(e) => setFormData({ ...formData, accountType: e.target.value })}
            >
              <MenuItem value="SAVINGS">Savings</MenuItem>
              <MenuItem value="CURRENT">Current</MenuItem>
            </Select>
          </FormControl>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={status === 'loading'}
        >
          Add Account
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// Withdrawal Dialog Component
const WithdrawalDialog = ({ open, onClose, walletBalance, bankAccounts, userId }) => {
  const dispatch = useDispatch()
  const { status } = useSelector((state) => state.payments)
  const [formData, setFormData] = useState({
    bankAccountId: '',
    amount: '',
  })
  const [errors, setErrors] = useState({})

  useEffect(() => {
    if (bankAccounts.length > 0 && !formData.bankAccountId) {
      const defaultAccount = bankAccounts.find((acc) => acc.isDefault) || bankAccounts[0]
      setFormData({ ...formData, bankAccountId: defaultAccount.id })
    }
  }, [bankAccounts, open])

  const handleSubmit = () => {
    const newErrors = {}
    if (!formData.bankAccountId) {
      newErrors.bankAccountId = 'Please select a bank account'
    }
    const amount = parseFloat(formData.amount)
    if (!amount || amount < 100) {
      newErrors.amount = 'Minimum withdrawal amount is ₹100'
    }
    if (amount > walletBalance) {
      newErrors.amount = `Insufficient balance. Available: ${formatCurrency(walletBalance)}`
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      return
    }

    dispatch(requestWithdrawal({ bankAccountId: formData.bankAccountId, amount })).then(
      (action) => {
        if (action.type.endsWith('fulfilled')) {
          // Refresh wallet balance after successful withdrawal
          if (userId) {
            dispatch(getWalletBalance(userId))
            dispatch(getWithdrawals())
          }
          onClose()
          setFormData({ bankAccountId: '', amount: '' })
          setErrors({})
        }
      }
    )
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Request Withdrawal</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="info">
            Available Balance: <strong>{formatCurrency(walletBalance)}</strong>
            <br />
            Minimum withdrawal: <strong>₹100</strong>
          </Alert>

          <FormControl fullWidth error={!!errors.bankAccountId}>
            <InputLabel>Bank Account</InputLabel>
            <Select
              value={formData.bankAccountId}
              label="Bank Account"
              onChange={(e) => setFormData({ ...formData, bankAccountId: e.target.value })}
            >
              {bankAccounts.map((account) => (
                <MenuItem key={account.id} value={account.id}>
                  {account.bankName} - {account.accountNumber}
                  {account.isDefault && ' (Default)'}
                </MenuItem>
              ))}
            </Select>
            {errors.bankAccountId && (
              <Typography variant="caption" color="error" sx={{ mt: 0.5, ml: 1.75 }}>
                {errors.bankAccountId}
              </Typography>
            )}
          </FormControl>

          <TextField
            label="Amount (₹)"
            type="number"
            fullWidth
            value={formData.amount}
            onChange={(e) =>
              setFormData({ ...formData, amount: e.target.value })
            }
            error={!!errors.amount}
            helperText={errors.amount || `Enter amount (min: ₹100, max: ${formatCurrency(walletBalance)})`}
            required
            inputProps={{ min: 100, max: walletBalance, step: 0.01 }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={status === 'loading' || bankAccounts.length === 0}
        >
          Request Withdrawal
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default PaymentsPage
