package com.astyann.gateway.exception;

/**
 * Thrown when a request arrives without a valid JWT token
 * on a protected route (401 Unauthorized).
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
