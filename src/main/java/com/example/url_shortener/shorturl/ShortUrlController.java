package com.example.url_shortener.shorturl;

import com.example.url_shortener.security.UserPrincipal;
import com.example.url_shortener.shorturl.dto.CreateShortUrlRequest;
import com.example.url_shortener.shorturl.dto.ShortUrlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shortUrlService.create(request, principal.getId()));
    }
}
