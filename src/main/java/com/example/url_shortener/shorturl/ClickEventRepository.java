package com.example.url_shortener.shorturl;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrlId(Long shortUrlId);

    @Query(
            value = """
                    SELECT date_trunc('day', clicked_at)::date AS day, count(*) AS clickCount
                    FROM click_events
                    WHERE short_url_id = :shortUrlId AND clicked_at >= :since
                    GROUP BY day
                    ORDER BY day
                    """,
            nativeQuery = true)
    List<DailyClickCountProjection> countByDaySince(
            @Param("shortUrlId") Long shortUrlId, @Param("since") java.time.Instant since);

    @Query(
            value = """
                    SELECT coalesce(nullif(referrer, ''), 'direct') AS referrer, count(*) AS clickCount
                    FROM click_events
                    WHERE short_url_id = :shortUrlId
                    GROUP BY referrer
                    ORDER BY count(*) DESC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<ReferrerCountProjection> topReferrers(@Param("shortUrlId") Long shortUrlId, @Param("limit") int limit);

    interface DailyClickCountProjection {
        LocalDate getDay();

        long getClickCount();
    }

    interface ReferrerCountProjection {
        String getReferrer();

        long getClickCount();
    }
}
