package com.astyann.gateway.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Uniform JSON error body returned by the gateway on security failures.
 * Using a record keeps it concise and immutable.
 */
public record GatewayErrorResponse(
        int    status,
        String error,
        String message,
        String path,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp
) {}
