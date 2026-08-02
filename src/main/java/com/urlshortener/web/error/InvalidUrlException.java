package com.urlshortener.web.error;

/** Thrown when a submitted URL fails safety/validation checks. Maps to HTTP 400. */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}
