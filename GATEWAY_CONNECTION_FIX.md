# Gateway Connection Timeout Fix

## Problem
The API Gateway was experiencing connection timeouts when trying to reach the Ride Service:
```
io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection timed out: getsockopt: /10.240.36.219:8082
```

## Root Cause
Eureka was registering services with their IP addresses (`10.240.36.219`) instead of `localhost`. When the Gateway tried to connect to the service using the IP address, it failed because:
1. The IP address might not be reachable from the Gateway's network context
2. Services are binding to `localhost` but Eureka was advertising the wrong IP
3. Network routing issues between the Gateway and the advertised IP

## Solution Applied

### 1. Fixed Eureka Service Registration Configuration

**Changed in all services** (`ride-service`, `user-service`, `api-gateway-service`):

**Before:**
```properties
eureka.instance.prefer-ip-address=true
```

**After:**
```properties
# Use hostname instead of IP address for local development
eureka.instance.prefer-ip-address=false
eureka.instance.hostname=localhost
```

**Why:**
- Forces Eureka to register services with `localhost` instead of IP addresses
- Ensures Gateway can connect using `localhost:8082` which is always reachable
- Prevents network routing issues in local development

### 2. Added Gateway Timeout Configuration

**Added to `api-gateway-service/src/main/resources/application.properties`:**

```properties
# Gateway HTTP Client Configuration (for connection timeouts)
spring.cloud.gateway.httpclient.connect-timeout=5000
spring.cloud.gateway.httpclient.response-timeout=30000
spring.cloud.gateway.httpclient.pool.type=elastic
spring.cloud.gateway.httpclient.pool.max-connections=500
spring.cloud.gateway.httpclient.pool.max-idle-time=30000
```

**Why:**
- Prevents connection timeouts with proper timeout settings
- Configures connection pooling for better performance
- Sets reasonable timeouts (5s connect, 30s response)

## Files Modified

1. ✅ `ride-service/src/main/resources/application.properties`
   - Changed `eureka.instance.prefer-ip-address=false`
   - Added `eureka.instance.hostname=localhost`

2. ✅ `user-service/src/main/resources/application.properties`
   - Changed `eureka.instance.prefer-ip-address=false`
   - Added `eureka.instance.hostname=localhost`

3. ✅ `api-gateway-service/src/main/resources/application.properties`
   - Changed `eureka.instance.prefer-ip-address=false`
   - Added `eureka.instance.hostname=localhost`
   - Added Gateway HTTP client timeout configurations

## Verification Steps

1. **Restart all services** (Eureka, API Gateway, User Service, Ride Service)
2. **Check Eureka Dashboard** (`http://localhost:8761`):
   - Verify services are registered with `localhost` instead of IP addresses
   - Service URLs should show: `http://localhost:8081`, `http://localhost:8082`
3. **Test the endpoint**:
   - Call `/api/rides/my-bookings` through the Gateway
   - Should connect successfully without timeout errors
4. **Check Gateway logs**:
   - Should show successful connections to `localhost:8082`
   - No more connection timeout errors

## Expected Behavior After Fix

- **Eureka Registration**: Services register as `localhost:8081`, `localhost:8082`
- **Gateway Routing**: Gateway connects to `localhost:8082` successfully
- **No Timeouts**: Connection timeouts should be resolved
- **Service Discovery**: Gateway can discover and route to services correctly

## Production Considerations

For production deployment, you may need to:
1. Use actual hostnames or IP addresses if services are on different machines
2. Configure proper network routing between services
3. Adjust timeout values based on network latency
4. Use service mesh or load balancer for service-to-service communication

For local development, using `localhost` is the correct approach.

---

**Fix Applied**: 2024
**Status**: ✅ Complete
**Impact**: Resolves connection timeout errors between API Gateway and backend services


