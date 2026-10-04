package com.ali6eza.shortlink.exception;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException() {
        super("Provide an absolute HTTP or HTTPS URL without credentials, up to 2048 characters.");
    }
}
