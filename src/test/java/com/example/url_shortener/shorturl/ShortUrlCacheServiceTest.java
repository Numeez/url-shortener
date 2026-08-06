package com.example.url_shortener.shorturl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ShortUrlCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ShortUrlCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new ShortUrlCacheService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(cacheService, "ttlSeconds", 3600L);
    }

    @Test
    void putStoresSerializedValueWithConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        CachedShortUrl cached = new CachedShortUrl(1L, "https://example.com", true, null);

        cacheService.put("abc1234", cached);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("shorturl:abc1234"), jsonCaptor.capture(), eq(Duration.ofSeconds(3600)));
        assertThat(jsonCaptor.getValue()).contains("https://example.com");
    }

    @Test
    void putThenGetRoundTripsTheValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        CachedShortUrl cached =
                new CachedShortUrl(1L, "https://example.com", true, Instant.parse("2030-01-01T00:00:00Z"));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        cacheService.put("abc1234", cached);
        verify(valueOperations).set(eq("shorturl:abc1234"), jsonCaptor.capture(), eq(Duration.ofSeconds(3600)));

        when(valueOperations.get("shorturl:abc1234")).thenReturn(jsonCaptor.getValue());

        assertThat(cacheService.get("abc1234")).isEqualTo(cached);
    }

    @Test
    void putThenGetRoundTripsANullExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        CachedShortUrl cached = new CachedShortUrl(1L, "https://example.com", true, null);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        cacheService.put("abc1234", cached);
        verify(valueOperations).set(eq("shorturl:abc1234"), jsonCaptor.capture(), eq(Duration.ofSeconds(3600)));

        when(valueOperations.get("shorturl:abc1234")).thenReturn(jsonCaptor.getValue());

        assertThat(cacheService.get("abc1234")).isEqualTo(cached);
    }

    @Test
    void getReturnsNullWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shorturl:missing")).thenReturn(null);

        assertThat(cacheService.get("missing")).isNull();
    }

    @Test
    void getReturnsNullForCorruptJsonInsteadOfThrowing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shorturl:corrupt")).thenReturn("not-json{{{");

        assertThat(cacheService.get("corrupt")).isNull();
    }

    @Test
    void evictDeletesTheKey() {
        cacheService.evict("abc1234");

        verify(redisTemplate).delete("shorturl:abc1234");
    }
}
