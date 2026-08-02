package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Analytics for a short code.
 *
 * <p>Scope reflects the normalized interpretation of the ambiguous "add analytics"
 * requirement (see SCENARIOS.md): total clicks (every redirect counts once), a
 * geographic breakdown, and a time-bucketed drill-down (day/week/month, selected via
 * {@code period}). Deliberately excluded from the prototype: unique-visitor counts
 * (would require identity we intentionally don't store) and bot filtering.
 */
public record StatsResponse(
        String code,
        long totalClicks,
        @Schema(description = "ISO-2 country code (or \"UNKNOWN\") -> click count.",
                example = "{\"US\": 12, \"IN\": 4, \"UNKNOWN\": 1}")
        Map<String, Long> clicksByCountry,
        @Schema(description = "Echoes the requested granularity, lowercased.", example = "day")
        String period,
        List<TimeBucket> clicksByPeriod
) {
    public record TimeBucket(
            @Schema(description = "Start of the bucket (Postgres date_trunc'd to the requested "
                    + "period, in the database session's timezone).")
            Instant bucketStart,
            long clicks
    ) {
    }
}
