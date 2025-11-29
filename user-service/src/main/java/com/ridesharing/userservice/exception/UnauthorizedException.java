package com.ridesharing.userservice.exception;

/**
 * Unauthorized Exception
 * Thrown when authentication or authorization fails
 */
public class UnauthorizedException extends RuntimeException {
    
    public UnauthorizedException(String message) {
        super(message);
    }
}

