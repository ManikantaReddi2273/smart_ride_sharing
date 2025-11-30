package com.ridesharing.paymentservice.exception;

/**
 * Resource Not Found Exception
 * Thrown when a requested resource is not found
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
