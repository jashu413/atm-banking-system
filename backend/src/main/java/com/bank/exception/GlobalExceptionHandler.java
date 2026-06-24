package com.bank.exception;

import com.bank.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps application, validation, and security exceptions to one JSON error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<Map<String, Object>> handleLockedUser(
            RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.LOCKED, ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "Access is denied.", req);
    }

    @ExceptionHandler({InvalidAmountException.class, InvalidPinException.class, InvalidTransferException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            BankingException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            AccountNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({InsufficientFundsException.class, WithdrawalLimitExceededException.class})
    public ResponseEntity<Map<String, Object>> handleUnprocessable(
            BankingException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(
            AccountLockedException ex, HttpServletRequest req) {
        return error(HttpStatus.LOCKED, ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest req) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT,
                "The account was modified concurrently. Please retry the operation.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception for path={}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", req);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message,
                                                       HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
