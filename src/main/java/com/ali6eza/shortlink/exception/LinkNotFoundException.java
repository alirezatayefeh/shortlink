package com.ali6eza.shortlink.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException() {
        super("Link not found.");
    }
}
