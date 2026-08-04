package com.example.url_shortener.shorturl.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record CreateShortUrlRequest(
        @NotBlank @Size(max = 2048) @URL String originalUrl,
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "must be 3-32 characters: letters, digits, - or _")
                String customAlias,
        @Future Instant expiresAt) {
}
