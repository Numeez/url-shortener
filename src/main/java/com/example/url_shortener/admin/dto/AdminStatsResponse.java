package com.example.url_shortener.admin.dto;

public record AdminStatsResponse(long totalUsers, long totalUrls, long activeUrls, long totalClicks) {
}
