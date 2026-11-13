package com.ali6eza.shortlink.exception;

public class InvalidExpirationException extends RuntimeException {
    public InvalidExpirationException() {
        super("expiresAt must be in the future.");
    }
}
