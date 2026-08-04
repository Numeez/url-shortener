package com.example.url_shortener.shorturl.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record UpdateShortUrlRequest(
        @Size(max = 2048) @URL String originalUrl,
        @Future Instant expiresAt,
        Boolean active) {
}
