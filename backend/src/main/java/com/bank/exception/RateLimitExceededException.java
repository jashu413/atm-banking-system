package com.bank.exception;

/** Thrown when a client exceeds the configured login rate limit. Maps to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
