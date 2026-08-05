package com.example.url_shortener.shorturl;

import java.time.Instant;

record CachedShortUrl(Long shortUrlId, String originalUrl, boolean active, Instant expiresAt) {
}
