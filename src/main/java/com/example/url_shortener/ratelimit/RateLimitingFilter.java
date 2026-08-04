package com.example.url_shortener.ratelimit;

import com.example.url_shortener.common.exception.ApiError;
import com.example.url_shortener.security.UserPrincipal;
import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Bucket> createBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> redirectBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        RateLimitTarget target = resolveTarget(request);
        if (target == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketFor(target);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded, retry after " + retryAfterSeconds + "s",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private RateLimitTarget resolveTarget(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equals(method) && "/api/urls".equals(path)) {
            return new RateLimitTarget(RateLimitCategory.CREATE, "user:" + currentUserKey(request));
        }
        if ("GET".equals(method) && path.startsWith("/r/")) {
            return new RateLimitTarget(RateLimitCategory.REDIRECT, "ip:" + clientIp(request));
        }
        if ("POST".equals(method) && ("/api/auth/login".equals(path) || "/api/auth/register".equals(path))) {
            return new RateLimitTarget(RateLimitCategory.AUTH, "ip:" + clientIp(request));
        }
        return null;
    }

    private Bucket bucketFor(RateLimitTarget target) {
        return switch (target.category()) {
            case CREATE -> createBuckets.computeIfAbsent(target.key(), k -> newBucket(properties.create()));
            case REDIRECT -> redirectBuckets.computeIfAbsent(target.key(), k -> newBucket(properties.redirect()));
            case AUTH -> authBuckets.computeIfAbsent(target.key(), k -> newBucket(properties.auth()));
        };
    }

    private Bucket newBucket(RateLimitProperties.Limit limit) {
        Bandwidth bandwidth = Bandwidth.classic(
                limit.capacity(),
                Refill.intervally(limit.refillTokens(), Duration.ofSeconds(limit.refillSeconds())));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private String currentUserKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return String.valueOf(principal.getId());
        }
        return clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
