package com.spe.calculator.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured error payload, e.g.
 * {"error":"Division by zero is not allowed","status":400,"timestamp":"2026-10-27T10:15:30.123Z"}.
 */
@JsonPropertyOrder({"error", "status", "timestamp"})
public record ErrorResponse(String error, int status, String timestamp) {

    public static ErrorResponse of(String error, int status) {
        return new ErrorResponse(error, status, Instant.now().toString());
    }
}
