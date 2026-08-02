
package com.urlshortener.service.analytics;

import com.urlshortener.domain.ShortUrl;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.web.dto.AdminUrlSummary;
import com.urlshortener.web.dto.StatsResponse;
import com.urlshortener.web.error.ShortUrlNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side analytics aggregation for a short code.
 *
 * <p>Interpretation of "a click" (the ambiguous requirement, normalized): one click =
 * one successful redirect. Totals are all-time; a geo breakdown uses the derived
 * country captured at click time. Unique visitors, bot filtering and time-series are
 * out of scope for the prototype and documented as such.
 */
@Service
public class StatsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public StatsService(ShortUrlRepository shortUrlRepository,
                        ClickEventRepository clickEventRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse statsFor(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));

        long total = clickEventRepository.countByShortUrlId(url.getId());

        Map<String, Long> byCountry = new LinkedHashMap<>();
        for (var row : clickEventRepository.countByCountry(url.getId())) {
            String country = row.getCountry() == null ? "UNKNOWN" : row.getCountry();
            byCountry.merge(country, row.getClicks(), Long::sum);
        }

        return new StatsResponse(code, total, byCountry);
    }

    /**
     * Admin listing: every short URL with its total click count, newest first.
     * Click totals are fetched as one grouped query rather than per-row, since this
     * scales with the number of URLs shown, not O(n) round trips.
     */
    @Transactional(readOnly = true)
    public List<AdminUrlSummary> listAll() {
        Map<Long, Long> clicksByUrlId = new HashMap<>();
        for (var row : clickEventRepository.countAllGroupedByShortUrl()) {
            clicksByUrlId.put(row.getShortUrlId(), row.getClicks());
        }

        return shortUrlRepository.findAll().stream()
                .sorted(Comparator.comparing(ShortUrl::getCreatedAt).reversed())
                .map(url -> new AdminUrlSummary(
                        url.getCode(),
                        url.getLongUrl(),
                        url.isCustomAlias(),
                        url.isActive(),
                        url.getCreatedAt(),
                        url.getExpiresAt(),
                        clicksByUrlId.getOrDefault(url.getId(), 0L)))
                .toList();
    }
}
