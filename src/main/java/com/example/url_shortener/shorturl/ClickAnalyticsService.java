package com.example.url_shortener.shorturl;

import com.example.url_shortener.shorturl.dto.AnalyticsResponse;
import com.example.url_shortener.shorturl.dto.DailyClickCount;
import com.example.url_shortener.shorturl.dto.ReferrerCount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClickAnalyticsService {

    private static final int LOOKBACK_DAYS = 30;
    private static final int TOP_REFERRERS_LIMIT = 5;

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlService shortUrlService;

    @Async("clickEventExecutor")
    public void recordClick(Long shortUrlId, String referrer, String userAgent, String ipAddress) {
        ClickEvent event = ClickEvent.builder()
                .shortUrl(shortUrlRepository.getReferenceById(shortUrlId))
                .referrer(referrer)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();
        clickEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(Long shortUrlId, Long ownerId) {
        ShortUrl shortUrl = shortUrlService.getOwned(shortUrlId, ownerId);

        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<DailyClickCount> clicksByDay = clickEventRepository.countByDaySince(shortUrlId, since).stream()
                .map(p -> new DailyClickCount(p.getDay(), p.getClickCount()))
                .toList();
        List<ReferrerCount> topReferrers = clickEventRepository.topReferrers(shortUrlId, TOP_REFERRERS_LIMIT).stream()
                .map(p -> new ReferrerCount(p.getReferrer(), p.getClickCount()))
                .toList();

        return new AnalyticsResponse(
                shortUrl.getId(), shortUrl.getShortCode(), shortUrl.getClickCount(), clicksByDay, topReferrers);
    }
}
