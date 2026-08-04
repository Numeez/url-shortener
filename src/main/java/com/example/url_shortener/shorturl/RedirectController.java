package com.example.url_shortener.shorturl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickAnalyticsService clickAnalyticsService;

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        RedirectResult result = shortUrlService.resolve(shortCode);

        clickAnalyticsService.recordClick(
                result.shortUrlId(),
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT),
                extractClientIp(request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.originalUrl())
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
