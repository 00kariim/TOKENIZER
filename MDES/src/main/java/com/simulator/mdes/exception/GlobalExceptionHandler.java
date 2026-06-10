package com.simulator.mdes.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler — translates service-layer exceptions to
 * RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Sensitive information (internal state, stack traces) is never included
 * in the response body. All detail messages are safe for external consumption.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, f ->
                        f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalid"));

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation Failed");
        detail.setType(URI.create("https://mdes.simulator/errors/validation"));
        detail.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(TokenizationException.class)
    public ResponseEntity<ProblemDetail> handleTokenization(TokenizationException ex) {
        log.warn("Tokenization failed: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        detail.setTitle("Tokenization Failed");
        detail.setDetail(ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(detail);
    }

    @ExceptionHandler(DuplicateTokenException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateTokenException ex) {
        log.warn("Duplicate token: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Duplicate Token");
        detail.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(InvalidCryptogramException.class)
    public ResponseEntity<ProblemDetail> handleCryptogram(InvalidCryptogramException ex) {
        log.warn("Cryptogram invalid: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        detail.setTitle("Cryptogram Validation Failed");
        detail.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail);
    }

    @ExceptionHandler({EntityNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource Not Found");
        detail.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> handleSecurity(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Domain Restriction Violation");
        detail.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Downstream service unavailable: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        detail.setTitle("Core Banking Unavailable");
        detail.setDetail("The payment processing service is temporarily unavailable. Please retry.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Internal Server Error");
        detail.setDetail("An unexpected error occurred. Please contact support.");
        return ResponseEntity.internalServerError().body(detail);
    }
}
