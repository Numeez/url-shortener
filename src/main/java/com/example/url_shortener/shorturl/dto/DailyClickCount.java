package com.example.url_shortener.shorturl.dto;

import java.time.LocalDate;

public record DailyClickCount(LocalDate date, long count) {
}
