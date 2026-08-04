package com.example.url_shortener.shorturl;

import com.example.url_shortener.common.exception.AliasAlreadyExistsException;
import com.example.url_shortener.common.exception.ShortUrlExpiredException;
import com.example.url_shortener.common.exception.ShortUrlNotFoundException;
import com.example.url_shortener.shorturl.dto.CreateShortUrlRequest;
import com.example.url_shortener.shorturl.dto.ShortUrlResponse;
import com.example.url_shortener.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request, Long ownerId) {
        String code = (request.customAlias() != null && !request.customAlias().isBlank())
                ? claimCustomAlias(request.customAlias())
                : generateUniqueCode();

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(code)
                .originalUrl(request.originalUrl())
                .owner(userRepository.getReferenceById(ownerId))
                .expiresAt(request.expiresAt())
                .build();

        shortUrlRepository.save(shortUrl);
        return toResponse(shortUrl);
    }

    @Transactional
    public String resolve(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        if (!shortUrl.isActive() || (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(Instant.now()))) {
            throw new ShortUrlExpiredException(shortCode);
        }

        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
        return shortUrl.getOriginalUrl();
    }

    private String claimCustomAlias(String alias) {
        if (shortUrlRepository.existsByShortCode(alias)) {
            throw new AliasAlreadyExistsException(alias);
        }
        return alias;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique short code after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private ShortUrlResponse toResponse(ShortUrl shortUrl) {
        return new ShortUrlResponse(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                baseUrl + "/r/" + shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getExpiresAt(),
                shortUrl.getCreatedAt(),
                shortUrl.getClickCount(),
                shortUrl.isActive());
    }
}
