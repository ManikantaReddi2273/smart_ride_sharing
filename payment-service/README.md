# Payment Service

Payment Processing and Wallet Management Service for Smart Ride Sharing System.

## Overview

This microservice handles all payment-related operations including:
- Payment gateway integration (Razorpay)
- Payment processing and verification
- Wallet management for drivers
- Transaction history
- Refund processing

## Port

**8084**

## Database

**payment_db** (MySQL)

## Configuration

### Required Properties

Update `application.properties` with your Razorpay credentials:

```properties
razorpay.key.id=your_razorpay_key_id
razorpay.key.secret=your_razorpay_key_secret
razorpay.mode=TEST  # or PRODUCTION

payment.platform.fee.percentage=10.0  # Platform commission (10%)
```

### Getting Razorpay Keys

1. Sign up at https://razorpay.com
2. Go to Dashboard → Settings → API Keys
3. Generate test keys for development
4. Copy Key ID and Key Secret to `application.properties`

## API Endpoints

### Payment Operations

- `POST /api/payments/initiate` - Create payment order
- `POST /api/payments/verify` - Verify payment signature
- `GET /api/payments/{paymentId}` - Get payment details
- `GET /api/payments/booking/{bookingId}` - Get payment by booking ID

### Transaction History

- `GET /api/payments/transactions/passenger/{passengerId}` - Get passenger transactions
- `GET /api/payments/transactions/driver/{driverId}` - Get driver transactions

### Wallet Operations

- `GET /api/payments/wallet/{userId}` - Get wallet balance
- `GET /api/payments/wallet/{userId}/transactions` - Get wallet transactions
- `POST /api/payments/wallet/credit/{paymentId}` - Credit driver wallet (after ride completion)

## Payment Flow

1. **Booking Creation** → Ride Service creates booking with PENDING status
2. **Payment Initiation** → Ride Service calls Payment Service to create Razorpay order
3. **Frontend Payment** → User completes payment via Razorpay checkout
4. **Payment Verification** → Frontend calls verify endpoint with payment details
5. **Booking Confirmation** → Ride Service confirms booking and updates seats
6. **Wallet Credit** → After ride completion, driver wallet is credited

## Database Schema

### Tables

- `payments` - Payment records
- `wallets` - User wallets
- `wallet_transactions` - Wallet transaction history
- `refunds` - Refund records

## Dependencies

- Spring Boot 3.2.0
- Spring Cloud 2023.0.0
- Razorpay Java SDK 1.4.3
- MySQL Connector
- Eureka Client

## Running the Service

1. Ensure MySQL is running and `payment_db` database exists
2. Update Razorpay credentials in `application.properties`
3. Ensure Eureka Server is running on port 8761
4. Run: `mvn spring-boot:run`

## Testing

Use Razorpay test mode for development:
- Test cards: https://razorpay.com/docs/payments/test-cards/
- Test UPI: success@razorpay
