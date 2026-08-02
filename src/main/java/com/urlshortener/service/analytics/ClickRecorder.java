package com.urlshortener.service.analytics;

import com.urlshortener.domain.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Records click events asynchronously and in a failure-isolated way.
 *
 * <p>Core principle (same instinct as any critical-path money system): analytics must
 * NEVER break or slow the redirect. Two guarantees:
 *  <ul>
 *    <li>Runs on the dedicated {@code clickExecutor} pool, off the redirect thread.</li>
 *    <li>Swallows and logs its own failures — an analytics DB hiccup never propagates
 *        back to the user's redirect.</li>
 *  </ul>
 *
 * <p>The raw IP is passed in, used only to derive geo, and never persisted.
 */
@Service
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final ClickEventRepository clickEventRepository;
    private final GeoResolver geoResolver;

    public ClickRecorder(ClickEventRepository clickEventRepository, GeoResolver geoResolver) {
        this.clickEventRepository = clickEventRepository;
        this.geoResolver = geoResolver;
    }

    @Async("clickExecutor")
    public void record(Long shortUrlId, String referrer, String userAgent, String ip) {
        try {
            // Derive geo, then discard the IP (never persisted).
            GeoResolver.Geo geo = geoResolver.resolve(ip);
            ClickEvent event = new ClickEvent(
                    shortUrlId, referrer, userAgent, geo.country(), geo.region());
            clickEventRepository.save(event);
        } catch (Exception e) {
            // Best-effort: a failed click record must not affect the redirect.
            log.warn("Failed to record click for shortUrlId={}: {}", shortUrlId, e.getMessage());
        }
    }
}
