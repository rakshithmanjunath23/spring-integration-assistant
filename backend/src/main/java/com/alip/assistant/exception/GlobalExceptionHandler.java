package com.alip.assistant.exception;

import com.alip.assistant.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException ex) {
        String fieldName = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField())
                .findFirst()
                .orElse(null);

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        String correlationId = UUID.randomUUID().toString();
        log.warn("Validation error [correlationId={}]: {}", correlationId, message);

        ErrorResponse response = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(message)
                .field(fieldName)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleServerWebInputException(ServerWebInputException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Input validation error [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(ex.getReason() != null ? ex.getReason() : "Invalid request input")
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Session not found [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("SESSION_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFileAccessDenied(FileAccessDeniedException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("File access denied [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("ACCESS_DENIED")
                .message(ex.getMessage())
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Service unavailable [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("SERVICE_UNAVAILABLE")
                .message(ex.getMessage())
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("Payload too large [correlationId={}]: {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("PAYLOAD_TOO_LARGE")
                .message("Request payload exceeds the maximum allowed size")
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
