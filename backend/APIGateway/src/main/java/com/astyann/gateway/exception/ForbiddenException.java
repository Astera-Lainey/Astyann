package com.astyann.gateway.exception;

/**
 * Thrown when a valid token is present but the user's role is
 * insufficient for the requested resource (403 Forbidden).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
