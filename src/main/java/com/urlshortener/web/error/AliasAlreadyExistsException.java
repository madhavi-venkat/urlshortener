package com.urlshortener.web.error;

/** Thrown when a requested custom alias is already in use. Maps to HTTP 409. */
public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("Custom alias already in use: " + alias);
    }
}
