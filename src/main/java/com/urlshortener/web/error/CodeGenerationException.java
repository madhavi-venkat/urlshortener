package com.urlshortener.web.error;

/**
 * Thrown when a unique random code could not be generated within the retry budget.
 * Maps to HTTP 503 — a transient server condition, not a client error.
 */
public class CodeGenerationException extends RuntimeException {
    public CodeGenerationException(String message) {
        super(message);
    }
}
