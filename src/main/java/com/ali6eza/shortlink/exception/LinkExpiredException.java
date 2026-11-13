package com.ali6eza.shortlink.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException() {
        super("Link has expired.");
    }
}
