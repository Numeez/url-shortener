package com.example.url_shortener.shorturl;

import com.example.url_shortener.security.UserPrincipal;
import com.example.url_shortener.shorturl.dto.AnalyticsResponse;
import com.example.url_shortener.shorturl.dto.CreateShortUrlRequest;
import com.example.url_shortener.shorturl.dto.ShortUrlResponse;
import com.example.url_shortener.shorturl.dto.UpdateShortUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private static final int MIN_QR_SIZE = 100;
    private static final int MAX_QR_SIZE = 1000;

    private final ShortUrlService shortUrlService;
    private final ClickAnalyticsService clickAnalyticsService;
    private final QrCodeService qrCodeService;

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shortUrlService.create(request, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<Page<ShortUrlResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(shortUrlService.listForOwner(principal.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shortUrlService.getForOwner(id, principal.getId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShortUrlResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShortUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shortUrlService.update(id, principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        shortUrlService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<AnalyticsResponse> analytics(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clickAnalyticsService.getAnalytics(id, principal.getId()));
    }

    @GetMapping(value = "/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(
            @PathVariable Long id,
            @RequestParam(defaultValue = "300") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        ShortUrlResponse shortUrl = shortUrlService.getForOwner(id, principal.getId());
        int clampedSize = Math.clamp(size, MIN_QR_SIZE, MAX_QR_SIZE);
        byte[] png = qrCodeService.generatePng(shortUrl.shortUrl(), clampedSize);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }
}
