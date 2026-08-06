package com.example.url_shortener.shorturl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.url_shortener.common.exception.AliasAlreadyExistsException;
import com.example.url_shortener.common.exception.ShortUrlExpiredException;
import com.example.url_shortener.common.exception.ShortUrlNotFoundException;
import com.example.url_shortener.shorturl.dto.CreateShortUrlRequest;
import com.example.url_shortener.shorturl.dto.ShortUrlResponse;
import com.example.url_shortener.shorturl.dto.UpdateShortUrlRequest;
import com.example.url_shortener.user.User;
import com.example.url_shortener.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShortUrlCacheService shortUrlCacheService;

    @InjectMocks
    private ShortUrlService shortUrlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortUrlService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void createGeneratesRandomCodeWhenNoAliasGiven() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", null, null);
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(false);

        ShortUrlResponse response = shortUrlService.create(request, 1L);

        assertThat(response.shortCode()).hasSize(7);
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/r/" + response.shortCode());
        assertThat(response.originalUrl()).isEqualTo("https://example.com");
        assertThat(response.active()).isTrue();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createUsesCustomAliasWhenProvided() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", "my-alias", null);
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(shortUrlRepository.existsByShortCode("my-alias")).thenReturn(false);

        ShortUrlResponse response = shortUrlService.create(request, 1L);

        assertThat(response.shortCode()).isEqualTo("my-alias");
    }

    @Test
    void createRejectsAliasThatIsAlreadyTaken() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", "taken-alias", null);
        when(shortUrlRepository.existsByShortCode("taken-alias")).thenReturn(true);

        assertThatThrownBy(() -> shortUrlService.create(request, 1L))
                .isInstanceOf(AliasAlreadyExistsException.class);

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void createRetriesCodeGenerationOnCollision() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", null, null);
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(true, true, false);

        ShortUrlResponse response = shortUrlService.create(request, 1L);

        assertThat(response.shortCode()).hasSize(7);
        verify(shortUrlRepository, times(3)).existsByShortCode(anyString());
    }

    @Test
    void createGivesUpAfterMaxGenerationAttempts() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com", null, null);
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> shortUrlService.create(request, 1L)).isInstanceOf(IllegalStateException.class);

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void resolveOnCacheHitIncrementsCountWithoutHittingDatabase() {
        CachedShortUrl cached = new CachedShortUrl(5L, "https://example.com/cached", true, null);
        when(shortUrlCacheService.get("abc1234")).thenReturn(cached);

        RedirectResult result = shortUrlService.resolve("abc1234");

        assertThat(result.shortUrlId()).isEqualTo(5L);
        assertThat(result.originalUrl()).isEqualTo("https://example.com/cached");
        verify(shortUrlRepository).incrementClickCount(5L);
        verify(shortUrlRepository, never()).findByShortCode(anyString());
    }

    @Test
    void resolveOnCacheHitButInactiveThrowsExpired() {
        CachedShortUrl cached = new CachedShortUrl(5L, "https://example.com/cached", false, null);
        when(shortUrlCacheService.get("abc1234")).thenReturn(cached);

        assertThatThrownBy(() -> shortUrlService.resolve("abc1234")).isInstanceOf(ShortUrlExpiredException.class);

        verify(shortUrlRepository, never()).incrementClickCount(any());
    }

    @Test
    void resolveOnCacheHitButPastExpiryThrowsExpired() {
        CachedShortUrl cached =
                new CachedShortUrl(5L, "https://example.com/cached", true, Instant.now().minusSeconds(60));
        when(shortUrlCacheService.get("abc1234")).thenReturn(cached);

        assertThatThrownBy(() -> shortUrlService.resolve("abc1234")).isInstanceOf(ShortUrlExpiredException.class);
    }

    @Test
    void resolveOnCacheMissLoadsFromDatabaseAndPopulatesCache() {
        when(shortUrlCacheService.get("abc1234")).thenReturn(null);
        ShortUrl shortUrl = ShortUrl.builder()
                .id(9L)
                .shortCode("abc1234")
                .originalUrl("https://example.com/db")
                .active(true)
                .clickCount(0L)
                .build();
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        RedirectResult result = shortUrlService.resolve("abc1234");

        assertThat(result.shortUrlId()).isEqualTo(9L);
        assertThat(result.originalUrl()).isEqualTo("https://example.com/db");
        assertThat(shortUrl.getClickCount()).isEqualTo(1L);
        verify(shortUrlCacheService).put(eq("abc1234"), any(CachedShortUrl.class));
    }

    @Test
    void resolveOnCacheMissAndNotFoundThrowsNotFound() {
        when(shortUrlCacheService.get("missing")).thenReturn(null);
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortUrlService.resolve("missing")).isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void resolveOnCacheMissAndInactiveThrowsExpiredWithoutCaching() {
        when(shortUrlCacheService.get("abc1234")).thenReturn(null);
        ShortUrl shortUrl = ShortUrl.builder()
                .id(9L)
                .shortCode("abc1234")
                .originalUrl("https://example.com/db")
                .active(false)
                .build();
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> shortUrlService.resolve("abc1234")).isInstanceOf(ShortUrlExpiredException.class);

        verify(shortUrlCacheService, never()).put(anyString(), any());
    }

    @Test
    void getForOwnerThrowsNotFoundWhenMissing() {
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortUrlService.getForOwner(1L, 99L))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void getForOwnerThrowsNotFoundWhenOwnedByAnotherUser() {
        ShortUrl shortUrl =
                ShortUrl.builder().id(1L).owner(User.builder().id(2L).build()).build();
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> shortUrlService.getForOwner(1L, 99L))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyProvidedFieldsAndEvictsCache() {
        ShortUrl shortUrl = ShortUrl.builder()
                .id(1L)
                .shortCode("abc1234")
                .originalUrl("https://old.example.com")
                .owner(User.builder().id(1L).build())
                .active(true)
                .build();
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(shortUrl));
        UpdateShortUrlRequest request = new UpdateShortUrlRequest("https://new.example.com", null, false);

        ShortUrlResponse response = shortUrlService.update(1L, 1L, request);

        assertThat(response.originalUrl()).isEqualTo("https://new.example.com");
        assertThat(response.active()).isFalse();
        verify(shortUrlCacheService).evict("abc1234");
    }

    @Test
    void deleteEvictsCacheAndRemovesEntity() {
        ShortUrl shortUrl = ShortUrl.builder()
                .id(1L)
                .shortCode("abc1234")
                .owner(User.builder().id(1L).build())
                .build();
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(shortUrl));

        shortUrlService.delete(1L, 1L);

        verify(shortUrlCacheService).evict("abc1234");
        verify(shortUrlRepository).delete(shortUrl);
    }
}
