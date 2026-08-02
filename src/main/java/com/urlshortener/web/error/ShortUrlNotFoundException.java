package com.urlshortener.web.error;

/** Thrown when a code does not exist or is no longer resolvable. Maps to HTTP 404. */
public class ShortUrlNotFoundException extends RuntimeException {
    public ShortUrlNotFoundException(String code) {
        super("No active short URL for code: " + code);
    }
}
