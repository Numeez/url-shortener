package com.example.url_shortener.admin.dto;

public record TopUrlResponse(
        Long id, String shortCode, String shortUrl, String originalUrl, String ownerEmail, long clickCount) {
}
