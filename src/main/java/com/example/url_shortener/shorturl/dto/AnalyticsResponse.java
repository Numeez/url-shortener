package com.example.url_shortener.shorturl.dto;

import java.util.List;

public record AnalyticsResponse(
        Long shortUrlId,
        String shortCode,
        long totalClicks,
        List<DailyClickCount> clicksByDay,
        List<ReferrerCount> topReferrers) {
}
