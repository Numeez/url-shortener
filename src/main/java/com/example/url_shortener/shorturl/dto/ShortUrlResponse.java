package com.example.url_shortener.shorturl.dto;

import java.time.Instant;

public record ShortUrlResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant expiresAt,
        Instant createdAt,
        long clickCount,
        boolean active) {
}
