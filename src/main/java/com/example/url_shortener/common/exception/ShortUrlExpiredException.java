package com.example.url_shortener.common.exception;

public class ShortUrlExpiredException extends RuntimeException {

    public ShortUrlExpiredException(String shortCode) {
        super("Short URL " + shortCode + " has expired or is inactive");
    }
}
