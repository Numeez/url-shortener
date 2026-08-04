package com.example.url_shortener.common.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String shortCode) {
        super("No short URL found for code " + shortCode);
    }

    public ShortUrlNotFoundException(Long id) {
        super("No short URL found with id " + id);
    }
}
