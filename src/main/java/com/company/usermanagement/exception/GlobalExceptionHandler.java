package com.company.usermanagement.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Centralized exception handling for all controllers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Business Exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex) {

        log.info("Resource not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(
            EmailAlreadyExistsException ex) {

        log.info("Email conflict: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex) {

        log.info("Bad credentials: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ─── Resilience Exceptions ────────────────────────────────────────────────

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            CallNotPermittedException ex) {

        log.error("Circuit Breaker is OPEN: {}", ex.getMessage());
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, 
                "Service is temporarily unavailable due to high load. Please try again in a few seconds.");
    }

    @ExceptionHandler({TimeoutException.class, java.util.concurrent.ExecutionException.class})
    public ResponseEntity<ErrorResponse> handleTimeout(
            Exception ex) {

        log.error("Request timed out or execution failed: {}", ex.getMessage());
        return buildError(HttpStatus.GATEWAY_TIMEOUT, 
                "The request took too long to process. Please try again.");
    }

    // ─── Validation Exceptions ─────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        log.warn("Validation failed: {}", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields are invalid")
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolations(
            ConstraintViolationException ex) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fieldErrors.putIfAbsent(
                    violation.getPropertyPath().toString(),
                    violation.getMessage());
        }

        log.warn("Constraint validation failed: {}", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more parameters are invalid")
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        if (isEmailConflict(ex)) {
            log.warn("Duplicate email conflict: {}", ex.getMostSpecificCause().getMessage());
            return buildError(HttpStatus.CONFLICT, "Email already registered");
        }

        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage(), ex);
        return buildError(HttpStatus.CONFLICT,
                "Request conflicts with existing data");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = String.format(
                "Parameter '%s' has invalid value: '%s'",
                ex.getName(), ex.getValue());

        log.warn("Type mismatch: {}", message);
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("Illegal argument: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ─── Security Exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex) {

        log.warn("Access denied: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    // ─── Catch-All ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildError(
            HttpStatus status, String message) {

        ErrorResponse error = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    private boolean isEmailConflict(Throwable throwable) {
        return com.company.usermanagement.config.ExceptionUtils.isEmailDuplicateError(throwable);
    }
}
