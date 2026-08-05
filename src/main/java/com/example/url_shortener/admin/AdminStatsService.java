package com.example.url_shortener.admin;

import com.example.url_shortener.admin.dto.AdminStatsResponse;
import com.example.url_shortener.admin.dto.TopUrlResponse;
import com.example.url_shortener.shorturl.ShortUrl;
import com.example.url_shortener.shorturl.ShortUrlRepository;
import com.example.url_shortener.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final ShortUrlRepository shortUrlRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalUrls = shortUrlRepository.count();
        long activeUrls = shortUrlRepository.countByActiveTrue();
        long totalClicks = shortUrlRepository.sumClickCount();
        return new AdminStatsResponse(totalUsers, totalUrls, activeUrls, totalClicks);
    }

    @Transactional(readOnly = true)
    public List<TopUrlResponse> getTopUrls(int limit) {
        return shortUrlRepository.findAllByOrderByClickCountDesc(PageRequest.of(0, limit))
                .map(this::toTopUrlResponse)
                .getContent();
    }

    private TopUrlResponse toTopUrlResponse(ShortUrl shortUrl) {
        return new TopUrlResponse(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                baseUrl + "/r/" + shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getOwner().getEmail(),
                shortUrl.getClickCount());
    }
}
