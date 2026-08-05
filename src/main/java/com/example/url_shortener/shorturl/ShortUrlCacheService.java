package com.example.url_shortener.shorturl;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class ShortUrlCacheService {

    private static final String KEY_PREFIX = "shorturl:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.short-url-ttl-seconds}")
    private long ttlSeconds;

    CachedShortUrl get(String shortCode) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CachedShortUrl.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    void put(String shortCode, CachedShortUrl cached) {
        String json = objectMapper.writeValueAsString(cached);
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, json, Duration.ofSeconds(ttlSeconds));
    }

    void evict(String shortCode) {
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }
}
