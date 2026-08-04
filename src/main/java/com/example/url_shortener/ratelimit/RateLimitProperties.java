package com.example.url_shortener.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Limit create, Limit redirect, Limit auth) {

    public record Limit(int capacity, int refillTokens, int refillSeconds) {
    }
}
