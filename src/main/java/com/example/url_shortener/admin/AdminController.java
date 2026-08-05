package com.example.url_shortener.admin;

import com.example.url_shortener.admin.dto.AdminStatsResponse;
import com.example.url_shortener.admin.dto.TopUrlResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MIN_TOP_URLS_LIMIT = 1;
    private static final int MAX_TOP_URLS_LIMIT = 100;

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }

    @GetMapping("/urls/top")
    public ResponseEntity<List<TopUrlResponse>> topUrls(@RequestParam(defaultValue = "10") int limit) {
        int clampedLimit = Math.clamp(limit, MIN_TOP_URLS_LIMIT, MAX_TOP_URLS_LIMIT);
        return ResponseEntity.ok(adminStatsService.getTopUrls(clampedLimit));
    }
}
