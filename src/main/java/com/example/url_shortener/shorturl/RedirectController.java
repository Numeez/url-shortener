package com.example.url_shortener.shorturl;

import java.net.URI;
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

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = shortUrlService.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }
}
