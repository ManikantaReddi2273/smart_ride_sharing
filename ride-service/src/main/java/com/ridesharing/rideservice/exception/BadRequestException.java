package com.ridesharing.rideservice.exception;

/**
 * Bad Request Exception
 * Thrown when a request is invalid or malformed
 */
public class BadRequestException extends RuntimeException {
    
    public BadRequestException(String message) {
        super(message);
    }
}

