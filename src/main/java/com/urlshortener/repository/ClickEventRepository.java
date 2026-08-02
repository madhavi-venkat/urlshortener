package com.urlshortener.repository;

import com.urlshortener.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrlId(Long shortUrlId);

    /** Click counts grouped by country (nulls collapse to "UNKNOWN" at the service layer). */
    @Query("""
            select ce.geoCountry as country, count(ce) as clicks
            from ClickEvent ce
            where ce.shortUrlId = :shortUrlId
            group by ce.geoCountry
            order by count(ce) desc
            """)
    List<CountryCount> countByCountry(@Param("shortUrlId") Long shortUrlId);

    interface CountryCount {
        String getCountry();
        long getClicks();
    }

    /** Total clicks per short URL, for the admin listing (avoids one count query per row). */
    @Query("""
            select ce.shortUrlId as shortUrlId, count(ce) as clicks
            from ClickEvent ce
            group by ce.shortUrlId
            """)
    List<ShortUrlClickCount> countAllGroupedByShortUrl();

    interface ShortUrlClickCount {
        Long getShortUrlId();
        long getClicks();
    }

    /**
     * Click counts bucketed by day/week/month via Postgres {@code date_trunc}. {@code unit}
     * is always one of the fixed literals from {@link com.urlshortener.service.analytics.StatsPeriod}
     * — never raw user input — but is passed as a bind parameter regardless, so it's safe
     * either way.
     */
    @Query(value = """
            select date_trunc(:unit, ce.occurred_at) as bucket, count(*) as clicks
            from click_event ce
            where ce.short_url_id = :shortUrlId
            group by 1
            order by 1
            """, nativeQuery = true)
    List<TimeBucketCount> countByTimeBucket(@Param("shortUrlId") Long shortUrlId, @Param("unit") String unit);

    interface TimeBucketCount {
        Instant getBucket();
        long getClicks();
    }
}
