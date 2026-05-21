package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured error response returned by the API on failures.
 * Every error response includes: error code, human-readable message,
 * ISO 8601 timestamp, and a unique correlationId (UUID).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private String field;
    private String timestamp;       // ISO 8601
    private String correlationId;   // UUID
}
