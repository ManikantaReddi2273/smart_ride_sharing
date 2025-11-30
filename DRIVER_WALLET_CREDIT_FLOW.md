# 💰 Complete Flow: How Money is Added to Driver's Wallet

## 📋 Overview

This document explains the **complete end-to-end flow** of how money gets added to a driver's wallet, with detailed examples and code references.

---

## 🔄 Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 1: PASSENGER BOOKS RIDE                 │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Frontend: SearchDialog.jsx                                     │
│  - User searches for rides                                     │
│  - Clicks "Book Seat"                                           │
│  - Enters seat count                                            │
│  - Clicks "Confirm Booking"                                    │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  API Call: POST /api/rides/{rideId}/book                        │
│  Service: RideService.bookSeat()                                │
│  Location: ride-service/src/main/java/.../RideService.java      │
│  Line: ~779                                                     │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 2: PAYMENT INITIATION                    │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  RideService.bookSeat() calls:                                  │
│  paymentServiceClient.initiatePayment(paymentRequest)           │
│                                                                  │
│  Payment Request Contains:                                      │
│  - bookingId: 123                                               │
│  - passengerId: 10                                              │
│  - driverId: 27                                                 │
│  - amount: 1000.00 (fare)                                       │
│  - fare: 1000.00                                                │
│  - currency: "INR"                                              │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Payment Service: PaymentService.initiatePayment()              │
│  Location: payment-service/.../PaymentService.java              │
│  Line: ~60-104                                                  │
│                                                                  │
│  Calculations:                                                  │
│  - Platform Fee = (fare × 10%) = ₹100                          │
│  - Total Amount = fare + platformFee = ₹1100                   │
│  - Amount in Paise = ₹1100 × 100 = 110000 paise                │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Payment Record Created in Database:                            │
│                                                                  │
│  payments table:                                                │
│  - id: 456                                                      │
│  - booking_id: 123                                              │
│  - passenger_id: 10                                             │
│  - driver_id: 27                                                │
│  - amount: 1100.00 (total paid by passenger)                   │
│  - fare: 1000.00 (driver earnings)                             │
│  - platform_fee: 100.00                                          │
│  - status: PENDING                                              │
│  - razorpay_order_id: "order_ABC123"                           │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 3: PASSENGER PAYS                       │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Frontend: PaymentDialog.jsx                                    │
│  - Payment dialog opens automatically                            │
│  - User clicks "Pay Now"                                        │
│  - Razorpay checkout opens                                      │
│  - User enters card details (test mode)                         │
│  - User completes payment                                       │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Razorpay Callback:                                             │
│  PaymentDialog.handlePaymentSuccess()                           │
│  - Dispatches verifyPayment action                              │
│  - Calls: POST /api/rides/bookings/{bookingId}/verify-payment  │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  RideService.verifyPaymentAndConfirmBooking()                    │
│  Location: ride-service/.../RideService.java                    │
│  Line: ~856                                                     │
│                                                                  │
│  Calls: paymentServiceClient.verifyPayment()                    │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Payment Service: PaymentService.verifyPayment()                │
│  Location: payment-service/.../PaymentService.java              │
│  Line: ~147-201                                                 │
│                                                                  │
│  Actions:                                                       │
│  1. Verifies Razorpay signature                                 │
│  2. Updates payment status: PENDING → SUCCESS                    │
│  3. Saves payment record                                        │
│                                                                  │
│  Payment Record Updated:                                        │
│  - status: SUCCESS ✅                                            │
│  - razorpay_payment_id: "pay_XYZ789"                           │
│  - razorpay_signature: "sig_ABC123"                             │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Booking Status Updated:                                        │
│  - status: PENDING → CONFIRMED ✅                                │
│  - Seats reserved                                                │
│  - Confirmation emails sent                                     │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 4: RIDE COMPLETION                      │
│                    (DRIVER MARKS RIDE AS COMPLETED)             │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Frontend: Driver marks ride as COMPLETED                       │
│  - Driver clicks "Complete Ride" button                         │
│  - API Call: PUT /api/rides/{rideId}/status                     │
│  - Body: { "status": "COMPLETED" }                              │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  RideService.updateRideStatus()                                 │
│  Location: ride-service/.../RideService.java                   │
│  Line: ~1156                                                    │
│                                                                  │
│  Actions:                                                       │
│  1. Validates driver owns the ride                              │
│  2. Updates ride status: BOOKED → COMPLETED                     │
│  3. Saves ride record                                           │
│                                                                  │
│  ✅ AUTOMATIC: Wallet credit is now automatic!                │
│  When ride status changes to COMPLETED, wallet is credited.  │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 5: WALLET CREDIT                        │
│              (MANUAL TRIGGER OR SCHEDULED JOB)                  │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  API Call: POST /api/payments/wallet/credit/{paymentId}        │
│  Called by: Ride Service (after ride completion)                │
│  OR: Scheduled job that processes completed rides                │
│  OR: Manual trigger from admin panel                            │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  PaymentController.creditDriverWallet()                         │
│  Location: payment-service/.../PaymentController.java           │
│  Line: ~190                                                     │
│                                                                  │
│  Actions:                                                       │
│  1. Gets payment by paymentId                                   │
│  2. Calls: walletService.creditDriverWalletAfterRideCompletion()│
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  WalletService.creditDriverWalletAfterRideCompletion()          │
│  Location: payment-service/.../WalletService.java                │
│  Line: ~147                                                     │
│                                                                  │
│  Validations:                                                   │
│  ✅ Payment status must be SUCCESS                              │
│  ✅ Payment must exist                                          │
│                                                                  │
│  Calculations:                                                 │
│  - driverEarnings = payment.getFare() = ₹1000.00               │
│  - description = "Earnings from booking #123"                  │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  WalletService.creditWallet()                                   │
│  Location: payment-service/.../WalletService.java                │
│  Line: ~69                                                      │
│                                                                  │
│  Actions:                                                       │
│  1. Gets or creates wallet for driver (userId = 27)            │
│  2. Calculates new balance:                                     │
│     - Old balance: ₹0.00                                        │
│     - Credit amount: ₹1000.00                                  │
│     - New balance: ₹1000.00 ✅                                  │
│  3. Updates wallet in database                                  │
│  4. Creates wallet transaction record                          │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Database Updates:                                              │
│                                                                  │
│  wallets table:                                                 │
│  - user_id: 27                                                  │
│  - balance: ₹1000.00 ✅ (updated)                              │
│  - currency: "INR"                                              │
│                                                                  │
│  wallet_transactions table:                                     │
│  - wallet_id: 1                                                 │
│  - type: CREDIT                                                 │
│  - amount: ₹1000.00                                            │
│  - balance_after: ₹1000.00                                     │
│  - description: "Earnings from booking #123"                   │
│  - payment_id: 456                                              │
│  - created_at: 2025-12-01 15:30:00                             │
└─────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ✅ DRIVER WALLET CREDITED!                    │
│                                                                  │
│  Driver can now see:                                            │
│  - Wallet Balance: ₹1000.00                                     │
│  - Transaction History: 1 transaction                           │
│  - On Driver Dashboard: WalletCard component                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Example Scenario

### Initial State:
- **Driver ID**: 27
- **Driver Wallet Balance**: ₹0.00
- **Ride Fare**: ₹1000.00
- **Platform Fee**: 10% = ₹100.00

### Step-by-Step:

#### 1. Passenger Books Ride
- Passenger searches for ride
- Clicks "Book Seat" → Booking #123 created
- **Booking Status**: PENDING

#### 2. Payment Initiated
- Payment #456 created
- **Payment Details**:
  - `amount`: ₹1100.00 (fare + platform fee)
  - `fare`: ₹1000.00 (driver earnings)
  - `platform_fee`: ₹100.00
  - `status`: PENDING

#### 3. Passenger Pays
- Payment dialog opens
- Passenger pays ₹1100.00 via Razorpay
- Payment verified → **Status**: SUCCESS ✅
- **Booking Status**: CONFIRMED ✅

#### 4. Ride Completed
- Driver marks ride as COMPLETED
- **Ride Status**: COMPLETED ✅

#### 5. Wallet Credit Triggered
- System calls: `POST /api/payments/wallet/credit/456`
- **Wallet Credit**:
  - Driver ID: 27
  - Amount: ₹1000.00 (fare, platform fee already deducted)
  - New Balance: ₹1000.00 ✅

### Final State:
- **Driver Wallet Balance**: ₹1000.00 ✅
- **Transaction Record**: Created ✅
- **Payment Status**: SUCCESS ✅
- **Ride Status**: COMPLETED ✅

---

## 🔍 Code Locations

### 1. Payment Initiation
**File**: `ride-service/src/main/java/.../RideService.java`
**Method**: `bookSeat()`
**Line**: ~779-836
```java
// Initiates payment
paymentOrderResponse = paymentServiceClient.initiatePayment(paymentRequest);
```

### 2. Payment Verification
**File**: `ride-service/src/main/java/.../RideService.java`
**Method**: `verifyPaymentAndConfirmBooking()`
**Line**: ~856-950
```java
// Verifies payment and confirms booking
Map<String, Object> verificationResponse = paymentServiceClient.verifyPayment(verificationRequest);
```

### 3. Payment Service - Initiate
**File**: `payment-service/src/main/java/.../PaymentService.java`
**Method**: `initiatePayment()`
**Line**: ~60-104
```java
// Calculates platform fee and creates payment
Double platformFee = (request.getFare() * platformFeePercentage) / 100.0;
Double totalAmount = request.getFare() + platformFee;
```

### 4. Payment Service - Verify
**File**: `payment-service/src/main/java/.../PaymentService.java`
**Method**: `verifyPayment()`
**Line**: ~147-201
```java
// Verifies Razorpay signature and updates payment status
payment.setStatus(PaymentStatus.SUCCESS);
```

### 5. Wallet Credit - Controller
**File**: `payment-service/src/main/java/.../PaymentController.java`
**Method**: `creditDriverWallet()`
**Line**: ~190-199
```java
@PostMapping("/wallet/credit/{paymentId}")
public ResponseEntity<Map<String, Object>> creditDriverWallet(@PathVariable Long paymentId) {
    Payment payment = paymentService.getPaymentById(paymentId);
    walletService.creditDriverWalletAfterRideCompletion(payment);
    // ...
}
```

### 6. Wallet Credit - Service
**File**: `payment-service/src/main/java/.../WalletService.java`
**Method**: `creditDriverWalletAfterRideCompletion()`
**Line**: ~147-160
```java
public void creditDriverWalletAfterRideCompletion(Payment payment) {
    // Validates payment status
    if (payment.getStatus() != PaymentStatus.SUCCESS) {
        throw new BadRequestException("Cannot credit wallet for non-successful payment");
    }
    
    // Credits wallet with fare amount (platform fee already deducted)
    Double driverEarnings = payment.getFare();
    creditWallet(payment.getDriverId(), driverEarnings, description, payment.getId());
}
```

### 7. Wallet Credit - Core Logic
**File**: `payment-service/src/main/java/.../WalletService.java`
**Method**: `creditWallet()`
**Line**: ~69-93
```java
public Wallet creditWallet(Long userId, Double amount, String description, Long paymentId) {
    // Gets or creates wallet
    Wallet wallet = getOrCreateWallet(userId);
    
    // Updates balance
    Double newBalance = wallet.getBalance() + amount;
    wallet.setBalance(newBalance);
    wallet = walletRepository.save(wallet);
    
    // Creates transaction record
    WalletTransaction transaction = new WalletTransaction();
    // ... sets transaction details
    walletTransactionRepository.save(transaction);
    
    return wallet;
}
```

---

## ⚠️ Important Notes

### ✅ Automatic Wallet Credit Implemented!

**The wallet credit is NOW automatically triggered when a ride is marked as COMPLETED!**

**Current Flow:**
1. ✅ Ride marked as COMPLETED
2. ✅ Wallet credit automatically triggered
3. ✅ All confirmed bookings with payments are processed

### Implementation Details:

#### ✅ Implemented: Automatic Wallet Credit in RideService.updateRideStatus()

**Location**: `ride-service/src/main/java/.../RideService.java`
**Method**: `updateRideStatus()`
**Line**: ~1177-1212

When ride status changes to COMPLETED, the system automatically:
1. Finds all CONFIRMED bookings for the ride
2. Credits driver wallet for each booking with a paymentId
3. Updates booking status to COMPLETED
4. Logs all operations for tracking

**Code**:
```java
if (status == RideStatus.COMPLETED) {
    // Get all confirmed bookings for this ride
    List<Booking> confirmedBookings = bookingRepository.findByRideIdAndStatus(
        rideId, BookingStatus.CONFIRMED);
    
    // Credit driver wallet for each confirmed booking with successful payment
    for (Booking booking : confirmedBookings) {
        if (booking.getPaymentId() != null) {
            paymentServiceClient.creditDriverWallet(booking.getPaymentId());
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        }
    }
}
```

#### Alternative Options (Not Needed Now):
Create a scheduled job that runs periodically and credits wallets for completed rides:

```java
@Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
public void processCompletedRides() {
    // Find rides marked as COMPLETED
    // Find bookings with SUCCESS payments
    // Credit driver wallets
}
```

#### Option 3: Manual Trigger
Add an endpoint for admins to manually trigger wallet credit:

```java
@PostMapping("/admin/wallet/credit-completed-rides")
public ResponseEntity<String> creditCompletedRides() {
    // Process all completed rides
    // Credit driver wallets
}
```

---

## 📈 Database Schema

### payments table:
```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,      -- Total paid (fare + platform fee)
    fare DECIMAL(10,2) NOT NULL,         -- Driver earnings
    platform_fee DECIMAL(10,2),         -- Platform commission
    status VARCHAR(20) NOT NULL,        -- PENDING, SUCCESS, FAILED
    razorpay_order_id VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    -- ... other fields
);
```

### wallets table:
```sql
CREATE TABLE wallets (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,     -- Driver ID
    balance DECIMAL(10,2) DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'INR',
    -- ... other fields
);
```

### wallet_transactions table:
```sql
CREATE TABLE wallet_transactions (
    id BIGINT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,          -- CREDIT, DEBIT
    amount DECIMAL(10,2) NOT NULL,
    balance_after DECIMAL(10,2) NOT NULL,
    description VARCHAR(255),
    payment_id BIGINT,                  -- Links to payment
    created_at TIMESTAMP,
    -- ... other fields
);
```

---

## 🎯 Summary

### Money Flow:
1. **Passenger pays**: ₹1100.00 (fare ₹1000 + platform fee ₹100)
2. **Platform keeps**: ₹100.00 (platform fee)
3. **Driver earns**: ₹1000.00 (fare amount)
4. **Driver wallet credited**: ₹1000.00 ✅

### When Wallet is Credited:
- ✅ Payment status: SUCCESS
- ✅ Ride status: COMPLETED
- ✅ System calls: `POST /api/payments/wallet/credit/{paymentId}`
- ✅ Wallet balance increases by fare amount

### Current Status:
- ✅ **Wallet credit is AUTOMATIC** when ride is completed
- ✅ **Manual trigger also works**: `POST /api/payments/wallet/credit/{paymentId}`
- ✅ **Implemented**: Automatic wallet credit in `updateRideStatus()` method

---

**This is the complete flow of how money is added to a driver's wallet!** 💰
